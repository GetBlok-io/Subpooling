package tasks

import actors.BlockingDbWriter.{UpdateBlockEffort, UpdatePoolBlockConf, UpdatePoolBlocksFound, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{BlockByHash, GetCurrentHeight, ValidateBlockByHeight}
import actors.PushMessageNotifier.BlockMessage
import actors.QuickDbReader.{BlockAtGEpoch, PoolBlocksByStatus, QueryBlocks, QueryPoolInfo, QuerySharesBefore, QuerySharesBetween}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import configs.TasksConfig.TaskConfiguration
import io.getblok.subpooling_core.explorer.Models.BlockContainer
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo, ValidBlock}
import play.api.{Configuration, Logger}
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolBlock, PoolInformation, Share}
import org.ergoplatform.appkit.ErgoId
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
@Singleton
class BlockStatusCheck @Inject()(system: ActorSystem, config: Configuration,
                                 @Named("quick-db-reader") query: ActorRef, @Named("blocking-db-writer") write: ActorRef,
                                 @Named("explorer-req-bus") explorerReqBus: ActorRef, @Named("push-msg-notifier") push: ActorRef,
                                  protected val dbConfigProvider: DatabaseConfigProvider)
                                    extends HasDatabaseConfigProvider[PostgresProfile]{
  import dbConfig.profile.api._
  val logger: Logger = Logger("BlockStatusCheck")
  val taskConfig: TaskConfiguration = new TasksConfig(config).blockCheckConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)



  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)

  if(taskConfig.enabled) {
    logger.info(s"BlockStatusCheck Task will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>
      logger.info("BlockStatusCheck has begun execution, now initiating block evaluation")
        val evalNewBlocks = Future(evaluateNewBlocks())(contexts.taskContext)
        evalNewBlocks.onComplete{
          case Success(value) =>
            logger.info("New block evaluation finished successfully")
            logger.info("Now initiating evaluation of confirming blocks")
            evalConfirmingBlocks()
            evalNullEffortBlocks
          case Failure(exception) =>
            logger.error("There was an error thrown during block evaluation", exception)
            logger.info("Now initiating evaluation of confirming blocks")
            evalConfirmingBlocks()
            evalNullEffortBlocks
        }(contexts.taskContext)


    })(contexts.taskContext)
  }else{
    logger.info("BlockStatusCheck Task was not enabled")
  }

  def evaluateNewBlocks(): Unit = {
    logger.info("Now evaluating pending blocks with confirmationNum 0")
    implicit val timeout: Timeout = Timeout(60 seconds)
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
    implicit val timeout: Timeout = Timeout(80 seconds)
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
    val futBlockHeight = (explorerReqBus ? GetCurrentHeight).mapTo[Int]
    for{
      poolInfoSeq <- blockPoolInfo
      height      <- futBlockHeight
    } yield {
      val blockGroups = blocksToCheck.groupBy(b => b.poolTag)
      blockGroups.foreach {
        bg =>
          val poolInfo = poolInfoSeq.find(info => info.poolTag == bg._1).get
          logger.info(s"==============Evaluating Confirming Blocks for Pool ${poolInfo.poolTag}==============")
          val blocksOrdered = bg._2.sortBy(b => b.blockheight)
          updateBlockEpochs(blocksOrdered, poolInfo)
          updateBlockConfirmations(blocksOrdered, poolInfo, height)
          // Finally, blocks are posted
          logger.info(s"==============Finished evaluating confirming blocks for pool ${bg._1}==============")

      }
    }
  }

  def evalNullEffortBlocks = {
    logger.info("Now evaluating blocks with null effort")
    implicit val ec: ExecutionContext = contexts.taskContext
    implicit val timeout: Timeout = Timeout(70 seconds)
    val allBlocks = (query ? QueryBlocks(None)).mapTo[Seq[PoolBlock]]
    allBlocks.map{
      blocks =>
        updateBlockEffort(blocks)
    }
  }

  def updateBlockEffort(allBlocks: Seq[PoolBlock]) = {
    implicit val ec: ExecutionContext = contexts.taskContext
    implicit val timeout: Timeout = Timeout(70 seconds)
    val blocksGrouped = allBlocks.groupBy(_.poolTag)
    var blocksUpdated = 0
    logger.info("Evaluating effort for blocks!")
    blocksGrouped.foreach{
      bg =>
        val ordered = bg._2.filter(_.gEpoch != -1).sortBy(_.gEpoch)
        val orderedNoEffort = ordered.filter(_.effort.isEmpty)
        logger.info(s"Pool ${bg._1} has ${orderedNoEffort.length} blocks with null effort values!")
        orderedNoEffort.take(2).foreach{
          block =>
            if(block.gEpoch != 1){
              val lastBlock = ordered.find(b => b.gEpoch == block.gEpoch - 1).get
              val start = lastBlock.created
              val end = block.created
              logger.info(s"Querying shares between last block ${lastBlock.blockheight} and current block ${block.blockheight}")
              val sharesBetween = db.run(Tables.PoolSharesTable.filter(_.created >= start).filter(_.created <= end).result)
              logger.info(s"Finished share query, now writing block effort for ${block.blockheight}")
              sharesBetween.map(s => writeEffortForBlock(block, s))
            }else{
              logger.info(s"gEpoch is 1 for block ${block.blockheight}, Not writing effort for block")
//              val date = block.created
//              val sharesBefore = db.run(Tables.PoolSharesTable.filter(_.created <= date).result)
//              logger.info(s"Finished share query, now writing block effort for ${block.blockheight}")
//              sharesBefore.map(s => writeEffortForBlock(block, s))
            }
        }
        blocksUpdated = blocksUpdated + orderedNoEffort.length
    }
    logger.info(s"Finished evaluating null effort blocks! A total $blocksUpdated blocks were updated")
  }

  def updateBlockEpochs(orderedBlocks: Seq[PoolBlock], poolInfo: PoolInformation) = {
    logger.info(s"Now updating block epochs for pool ${poolInfo.poolTag}. Pool currently has ${poolInfo.blocksFound} blocks found.")
    logger.info(s"There ${orderedBlocks.length} blocks being evaluated for the pool.")
    val start = poolInfo.blocksFound
    val blocksToEval = orderedBlocks.filter(_.gEpoch == -1).sortBy(_.blockheight)
    blocksToEval.zipWithIndex.foreach{
      blockZip =>
        write ! UpdatePoolBlockConf(blockZip._1.status, blockZip._1.confirmation, blockZip._1.blockheight, Some(start + blockZip._2 + 1))
    }
    logger.info(s"Finished adding gEpochs to all blocks for pool ${poolInfo.poolTag}.")
    logger.info(s"New blocks found for pool ${poolInfo.poolTag}:")
    logger.info(s"NewBlocksFound: ${poolInfo.blocksFound + blocksToEval.length}")
    write ! UpdatePoolBlocksFound(poolInfo.poolTag, poolInfo.blocksFound + blocksToEval.length)
  }
  def updateBlockConfirmations(orderedBlocks: Seq[PoolBlock], poolInfo: PoolInformation, height: Long) = {
    logger.info(s"Now updating block confirmations for pool ${poolInfo.poolTag}")
    logger.info(s"Currently evaluating ${orderedBlocks.length} confirming blocks for the pool.")
    var confirming = 0
    var confirmed = 0
    val updatedBlocks = orderedBlocks.map {
      block =>
        if (height > block.blockheight) {
          logger.info(s"New confirmation update being made for block ${block.blockheight}")
          val newConfirmationNumber = BigDecimal((height - block.blockheight)) / params.confirmationNum
          val nextConfirmations = Math.min(newConfirmationNumber.toDouble, 1.0)
          var nextStatus = PoolBlock.CONFIRMING
          if (nextConfirmations == 1.0) {

            if (block.gEpoch != -1) {
              nextStatus = PoolBlock.CONFIRMED
              push ! BlockMessage(block, poolInfo)
              confirmed = confirmed + 1
            }else{
              confirming = confirming + 1
            }
          }else{
            confirming = confirming + 1
          }

          /*if(block.gEpoch != -1){
            nextGEpoch = block.gEpoch
          }*/
          logger.info(s"Updating block ${block.blockheight} with status $nextStatus and confirmations $nextConfirmations")
          write ! UpdatePoolBlockConf(nextStatus, nextConfirmations, block.blockheight, None)
        }
    }
    logger.info(s"Finished evaluating confirmations for pool ${poolInfo.poolTag}")
    logger.info(s"The pool has ${confirmed} confirmed blocks and ${confirming} confirming blocks after evaluation.")
  }




  def validateBlockAsync(blockHeight: Long): Future[Option[NodeHandler.PartialBlockInfo]] = {
    implicit val timeout: Timeout = Timeout(70 seconds)
    (explorerReqBus ? ValidateBlockByHeight(blockHeight)).mapTo[Option[NodeHandler.PartialBlockInfo]]
  }

  def executeInitBlockValidation(blockHeight: Long, validation: Future[Option[NodeHandler.PartialBlockInfo]]): Unit = {
    implicit val timeout: Timeout = Timeout(70 seconds)
    validation.onComplete{
      case Success(value) =>
        value match {
          case Some(partialBlockInfo: PartialBlockInfo) =>
            val blockUpdate = write ? UpdateWithValidation(blockHeight, partialBlockInfo)

            partialBlockInfo match{
              case OrphanBlock(reward, txConf, hash) =>
                logger.warn(s"Block at height $blockHeight was found to be an orphan")
              case ValidBlock(reward, txConf, hash) =>
                logger.info(s"Block at height $blockHeight was successfully validated and set to confirming")
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

  def writeEffortForBlock(currentBlock: PoolBlock, shares: Seq[Share]): Unit = {
    logger.info(s"Writing effort for current block ${currentBlock.blockheight}")
    val accumulatedDiff = shares.map(s => BigDecimal(s.difficulty)).sum * AppParameters.shareConst
    logger.info(s"Accumulated effort calculated: ${accumulatedDiff}")
    val totalEffort = accumulatedDiff / currentBlock.netDiff
    logger.info(s"Now updating block effort for block ${currentBlock} and poolTag ${currentBlock}")
    logger.info(s"Effort: ${(totalEffort * 100).toDouble}% ")
    write ! UpdateBlockEffort(currentBlock.poolTag, totalEffort.toDouble, currentBlock.blockheight)
  }
}
