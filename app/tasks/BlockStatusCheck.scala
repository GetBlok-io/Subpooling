package tasks

import actors.BlockingDbWriter.{UpdateBlockConf, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{BlockByHash, GetCurrentHeight, ValidateBlockByHeight}
import actors.QuickDbReader.BlocksByStatus
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import configs.TasksConfig.TaskConfiguration
import io.getblok.subpooling_core.explorer.Models.BlockContainer
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo, ValidBlock}
import play.api.{Configuration, Logger}
import io.getblok.subpooling_core.persistence.models.Models.Block
import org.ergoplatform.appkit.ErgoId

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
@Singleton
class BlockStatusCheck @Inject()(system: ActorSystem, config: Configuration,
                                @Named("quick-db-reader") quickQuery: ActorRef, @Named("blocking-db-writer") slowWrite: ActorRef,
                                @Named("explorer-req-bus") explorerReqBus: ActorRef) {
  val logger: Logger = Logger("BlockStatusCheck")
  val taskConfig: TaskConfiguration = new TasksConfig(config).blockCheckConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)



  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)

  if(taskConfig.enabled) {
    logger.info(s"BlockStatusCheck Task will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleAtFixedRate(initialDelay = taskConfig.startup, interval = taskConfig.interval)({
      () =>
      logger.info("BlockStatusCheck has begun execution, now initiating block evaluation")
        val tryEvaluate = Try
        {
          evaluateNewBlocks()
          evaluateInitiatedBlocks()
        }
        tryEvaluate match {
          case Success(value) =>
            logger.info("Block evaluation exited successfully")
          case Failure(exception) =>
            logger.error("There was an error thrown during block evaluation", exception)
        }

    })(contexts.taskContext)
  }else{
    logger.info("BlockStatusCheck Task was not enabled")
  }

  def evaluateNewBlocks(): Unit = {
    logger.info("Now evaluating pending blocks with confirmationNum 0")
    implicit val timeout: Timeout = Timeout(30 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val pendingBlocksResult = quickQuery ? BlocksByStatus(Block.PENDING)
    val pendingBlocks = Await.result(pendingBlocksResult, timeout.duration).asInstanceOf[Seq[Block]]

    if (pendingBlocks.isEmpty) {
      logger.warn("No pending blocks found, evaluation for these blocks will now terminate")
      return
    }

    logger.info(s"Total of ${pendingBlocks.size} pending blocks found in database! " +
      s"Initiating validation for up to ${params.pendingBlockNum} of these blocks")

    pendingBlocks.take(params.pendingBlockNum).map(b => b -> validateBlockAsync(b.blockheight)).foreach(fb => executeInitBlockValidation(fb._1.blockheight, fb._2))
  }

  def evaluateInitiatedBlocks(): Unit = {
    logger.info("Now evaluating initiated blocks")
    implicit val timeout: Timeout = Timeout(15 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val initBlocksResult = quickQuery ? BlocksByStatus(Block.INITIATED)
    val initBlocks = Await.result(initBlocksResult, timeout.duration).asInstanceOf[Seq[Block]].sortBy(b => b.blockheight)

    if(initBlocks.isEmpty) {
      logger.warn("No initated blocks were found")
      return
    }

    logger.info(s"Total of ${initBlocks.size} initiated blocks found in database! Only a max of ${params.pendingBlockNum}" +
      s" initiated blocks will be evaluated")

    val blocksToCheck = initBlocks.filter(b => b.hash != null && b.reward != 0.0)

    val blockChecks = blocksToCheck.take(params.pendingBlockNum).map(b => (b, (explorerReqBus ? GetCurrentHeight).mapTo[Int]))
    blockChecks.foreach{
      f =>
        f._2.onComplete{
          case Success(height) =>
            logger.info("Current height request returned from explorerReqBus")
            val block = f._1
            if(height > block.blockheight) {
              logger.info(s"New confirmation update being made for block ${block.blockheight}")
              val newConfirmationNumber = BigDecimal((height - block.blockheight)) / params.confirmationNum
              val nextConfirmations = Math.min(newConfirmationNumber.toDouble, 1.0)
              var nextStatus = Block.INITIATED
              if(nextConfirmations == 1.0)
                nextStatus = Block.CONFIRMED
              logger.info(s"Updating block ${f._1.blockheight} with status $nextStatus and confirmations $nextConfirmations")
              val blockUpdate = slowWrite ? UpdateBlockConf(nextStatus, nextConfirmations, f._1.blockheight)

              blockUpdate.mapTo[Long].onComplete{
                case Success(rows) =>
                  if(rows > 0)
                    logger.info(s"Successfully updated block ${block.blockheight}")
                  else {
                    logger.error(s"Block ${block.blockheight} could not be updated!")
                  }
                case Failure(t) =>
                  logger.error(s"There was an error updating block ${block.blockheight}")
                  logger.error("The following error occurred: ", t)
              }
            }else if(block.blockheight == height){
              logger.info(s"Block ${block.blockheight} has same height as current height $height, skipping confirmation updates.")
            }else{
              logger.warn(s"Block ${block.blockheight} has a larger height than the current height, maybe a node is desynced?")
              logger.warn(s"Skipping confirmation updates for Block ${block.blockheight}")
            }
          case Failure(t) =>

            logger.error(s"There was a critical error while evaluating block ${f._1.blockheight}", t)
            logger.warn(s"No updates being made for block ${f._1.blockheight}")

        }
    }
  }

  def validateBlockAsync(blockHeight: Long): Future[Option[NodeHandler.PartialBlockInfo]] = {
    implicit val timeout: Timeout = Timeout(30 seconds)
    (explorerReqBus ? ValidateBlockByHeight(blockHeight)).mapTo[Option[NodeHandler.PartialBlockInfo]]
  }

  def executeInitBlockValidation(blockHeight: Long, validation: Future[Option[NodeHandler.PartialBlockInfo]]): Unit = {
    implicit val timeout: Timeout = Timeout(30 seconds)
    validation.onComplete{
      case Success(value) =>
        value match {
          case Some(partialBlockInfo: PartialBlockInfo) =>
            val blockUpdate = slowWrite ? UpdateWithValidation(blockHeight, partialBlockInfo)

            partialBlockInfo match{
              case OrphanBlock(reward, txConf, hash) =>
                logger.warn(s"Block at height $blockHeight was found to be an orphan")
              case ValidBlock(reward, txConf, hash) =>
                logger.info(s"Block at height $blockHeight was successfully validated and set to initiated")
              case NodeHandler.ConfirmedBlock(reward, txConf, hash) =>
                logger.info(s"Block at height $blockHeight was successfully validated and set to confirmed")
            }

            blockUpdate.onComplete{
              case Success(value) =>
                val rows = value.asInstanceOf[Long]
                if(rows > 0) {
                  logger.info("Block updated successfully")
                } else {
                  logger.warn(s"Block $blockHeight was not updated!")
                }
              case Failure(exception) =>
                logger.error("An exception was thrown during database updates for initial block validation", exception)
            }(contexts.taskContext)
          case None =>
            logger.warn(s"Block validation failed for block $blockHeight. ")
        }
      case Failure(exception) =>
        logger.warn("There was an exception thrown during block validation", exception)
    }(contexts.taskContext)
  }

}
