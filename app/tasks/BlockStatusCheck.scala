package tasks

import actors.BlockingDbWriter.{UpdatePoolBlockConf, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{BlockByHash, GetCurrentHeight, ValidateBlockByHeight}
import actors.QuickDbReader.{PoolBlocksByStatus, QueryPoolInfo}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import configs.TasksConfig.TaskConfiguration
import io.getblok.subpooling_core.explorer.Models.BlockContainer
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo, ValidBlock}
import play.api.{Configuration, Logger}
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolBlock, PoolInformation}
import org.ergoplatform.appkit.ErgoId

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
@Singleton
class BlockStatusCheck @Inject()(system: ActorSystem, config: Configuration,
                                 @Named("quick-db-reader") query: ActorRef, @Named("blocking-db-writer") write: ActorRef,
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
          evalConfirmingBlocks()
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
    val validatingBlocksResult = query ? PoolBlocksByStatus(PoolBlock.VALIDATING)
    val validatingBlocks = Await.result(validatingBlocksResult, timeout.duration).asInstanceOf[Seq[PoolBlock]]

    if (validatingBlocks.isEmpty) {
      logger.warn("No validating blocks found, evaluation for these blocks will now terminate")
      return
    }

    logger.info(s"Total of ${validatingBlocks.size} validating blocks found in database! " +
      s"Initiating validation for up to ${params.pendingBlockNum} of these blocks")

    val blocksToCheck = validatingBlocks.take(params.pendingBlockNum)
    blocksToCheck.map(block => block -> validateBlockAsync(block.blockheight)).foreach(bv => executeInitBlockValidation(bv._1.blockheight, bv._2))
  }

  def evalConfirmingBlocks(): Unit = {
    logger.info("Now evaluating confirming blocks")
    implicit val timeout: Timeout = Timeout(15 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val initBlocksResult = query ? PoolBlocksByStatus(PoolBlock.CONFIRMING)
    val initBlocks = Await.result(initBlocksResult, timeout.duration).asInstanceOf[Seq[PoolBlock]].sortBy(b => b.blockheight)

    if(initBlocks.isEmpty) {
      logger.warn("No confirming blocks were found")
      return
    }

    logger.info(s"Total of ${initBlocks.size} confirming blocks found in database! Only a max of ${params.pendingBlockNum}" +
      s" confirming blocks will be evaluated")

    val blocksToCheck = initBlocks.filter(b => b.hash != null && b.reward != 0.0).take(params.pendingBlockNum)

    val blockPoolInfo = Future.sequence(blocksToCheck.map(b => (query ? QueryPoolInfo(b.poolTag)).mapTo[PoolInformation]))
    val futBlockHeight = (query ? GetCurrentHeight).mapTo[Int]
    for{
      poolInfoSeq <- blockPoolInfo
      height      <- futBlockHeight
    } yield {
      val blockGroups = blocksToCheck.groupBy(b => b.poolTag)
      blockGroups.foreach {
        bg =>
          val poolInfo = poolInfoSeq.find(info => info.poolTag == bg._1).get
          val blocksOrdered = bg._2.sortBy(b => b.blockheight)
          val blocksZipped = blocksOrdered.zipWithIndex.map(bz => bz._1 -> (bz._2 + 1L + poolInfo.blocksFound))
          blocksZipped.foreach{
            bz =>
              val block = bz._1
              var nextGEpoch = bz._2
              if(height > block.blockheight){
                logger.info(s"New confirmation update being made for block ${block.blockheight}")
                  val newConfirmationNumber = BigDecimal((height - block.blockheight)) / params.confirmationNum
                  val nextConfirmations = Math.min(newConfirmationNumber.toDouble, 1.0)
                  var nextStatus = PoolBlock.CONFIRMING
                  if(nextConfirmations == 1.0) {
                    nextStatus = PoolBlock.CONFIRMED
                  }else{
                    nextGEpoch = -1
                  }
                  logger.info(s"Updating block ${block.blockheight} with status $nextStatus, confirmations $nextConfirmations," +
                    s" and GEpoch $nextGEpoch")
                  write ! UpdatePoolBlockConf(nextStatus, nextConfirmations, block.blockheight, nextGEpoch)
              }
          }
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
            val blockUpdate = write ? UpdateWithValidation(blockHeight, partialBlockInfo)

            partialBlockInfo match{
              case OrphanBlock(reward, txConf, hash) =>
                logger.warn(s"Block at height $blockHeight was found to be an orphan")
              case ValidBlock(reward, txConf, hash) =>
                logger.info(s"Block at height $blockHeight was successfully validated and set to initiated")
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
