package tasks

import actors.BlockingDbWriter.{DeletePlacementsAtBlock, InsertMembers, InsertPlacements, UpdateBlockConf, UpdateBlockStatus, UpdatePoolGEpoch, UpdateWithNewStates, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{BlockByHash, ValidateBlockByHeight}
import actors.GroupRequestHandler.{ConstructDistribution, ConstructHolding, DistributionComponents, DistributionResponse, ExecuteDistribution, ExecuteHolding, FailedPlacements, HoldingComponents, HoldingResponse}
import actors.QuickDbReader.{BlockByHeight, BlocksByStatus, MinersByAssignedPool, PlacementsByBlock, QueryAllSubPools, QueryLastPlacement, QueryPoolInfo, QueryWithShareHandler, SettingsForMiner}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.explorer.Models.BlockContainer
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups.stages.{DistributionRoot, HoldingRoot}
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.payments.ShareCollector
import io.getblok.subpooling_core.persistence.models.Models.{Block, MinerSettings, PoolInformation, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{ErgoClient, ErgoId, InputBox, Parameters}
import org.ergoplatform.wallet.boxes.BoxSelector
import play.api.{Configuration, Logger}

import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.{Inject, Named, Singleton}
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Singleton
class GroupExecutionTask @Inject()(system: ActorSystem, config: Configuration,
                                   @Named("quick-db-reader") quickQuery: ActorRef, @Named("blocking-db-writer") slowWrite: ActorRef,
                                   @Named("explorer-req-bus") explorerReqBus: ActorRef, @Named("group-handler") groupHandler: ActorRef) {
  val logger: Logger = Logger("GroupExecution")
  val taskConfig: TaskConfiguration = new TasksConfig(config).groupExecConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val ergoClient: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)
  var loadedBoxes: ConcurrentLinkedQueue[InputBox] = new ConcurrentLinkedQueue[InputBox]()
  if(taskConfig.enabled) {
    logger.info(s"GroupExecution Task will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleAtFixedRate(initialDelay = taskConfig.startup, interval = taskConfig.interval)({
      () =>
      logger.info("GroupExecution has begun")
        loadedBoxes = new ConcurrentLinkedQueue[InputBox]()
        val tryPreCollection = Try {
          loadedBoxes = preLoadInputBoxes(params.amountToPreCollect)
        }
        if(tryPreCollection.isSuccess) {

          val tryPlacement = Try {
            PlacementFunctions.executePlacement()
          }
          tryPlacement match {
            case Success(value) =>
              logger.info("Synchronous placement functions executed successfully!")
            case Failure(exception) =>
              logger.error("There was a fatal error thrown during synchronous placement execution", exception)
              logger.info("Now initiating distribution")
              val tryDistribute = Try {
                DistributionFunctions.executeDistribution()
              }
              tryDistribute match{
                case Success(value) =>
                  logger.info("Synchronous distribution functions executed successfully!")

                case Failure(exception) =>
                  logger.error("There was a fatal error thrown during synchronous distribution execution", exception)
              }
          }


        }else{
          logger.error("There was an error thrown while trying to pre-collect inputs!", tryPreCollection.failed.get)
        }

    })(contexts.taskContext)
  }else{
    logger.info("GroupExecution Task was not enabled")
  }
  case class BlockSelections(block: Block, poolTag: String)

  def selectBlocks(blocks: Seq[Block], distinctOnly: Boolean): Seq[BlockSelections] = {
    implicit val timeout: Timeout = Timeout(20 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    logger.info("Now selecting blocks with unique pool tags")
    val selectedBlocks = blocks.map {
      block =>
        logger.info(s"Getting pool for block ${block.blockheight} with miner ${block.miner}")
        val settingsQuery = Await.result((quickQuery ? SettingsForMiner(block.miner)).mapTo[MinerSettings].map(s => s.subpool), timeout.duration)
        BlockSelections(block, settingsQuery)
    }
    if(distinctOnly) {
      val distinctBlocks = ArrayBuffer.empty[BlockSelections]
      for (blockSel <- selectedBlocks) {
        if (!distinctBlocks.exists(bs => bs.poolTag == blockSel.poolTag)) {
          logger.info(s"Unique pool tag ${blockSel.poolTag} was added to selection!")
          distinctBlocks += blockSel
        }
      }
      distinctBlocks.toSeq.take(params.pendingBlockNum)
    }else{
      selectedBlocks.take(params.pendingBlockNum)
    }
  }

  def makeBlockBoxMap(blockSelections: Seq[BlockSelections], collectedInputs: ArrayBuffer[InputBox], maxInputs: Long): Map[Long, Seq[InputBox]] = {
    var blockAmountMap = Map.empty[Long, Seq[InputBox]]
    for (blockSel <- blockSelections) {
      var blockAmount = 0L
      var inputsForBlock = collectedInputs.indices.takeWhile {
        idx =>
          blockAmount = blockAmount + collectedInputs(idx).getValue
          // Not final, so keep iterating
          if (blockAmount < maxInputs) {
            true
          } else {
            // This index is final box needed, so return true one more time
            if (blockAmount - collectedInputs(idx).getValue <= maxInputs) {
              true
            } else {
              // This box is not needed to be greater than max, now returning
              blockAmount = blockAmount - collectedInputs(idx).getValue
              false
            }
          }
      }.map(idx => collectedInputs(idx))
      logger.info(s"Total of ${inputsForBlock.size} boxes with $blockAmount value")
      collectedInputs --= inputsForBlock
      logger.info("Adding block and boxes to map")
      blockAmountMap = blockAmountMap + (blockSel.block.blockheight -> inputsForBlock.toSeq)
    }
    blockAmountMap
  }

  def preLoadInputBoxes(amountToFind: Long): ConcurrentLinkedQueue[InputBox] = {
    val collectedInputs = ArrayBuffer() ++ ergoClient.execute {
      ctx =>
        ctx.getWallet.getUnspentBoxes(amountToFind).get()
    }.asScala.toSeq.sortBy(b => b.getValue.toLong).reverse
    collectedInputs.foreach{
      ib => loadedBoxes.add(ib)
    }
    loadedBoxes
  }

  def collectFromLoaded(amountToCollect: Long): ArrayBuffer[InputBox] = {
    var currentSum = 0L
    val boxesCollected = ArrayBuffer.empty[InputBox]
    while(currentSum < amountToCollect){
      val polledBox = loadedBoxes.poll()
      boxesCollected += polledBox
      currentSum = currentSum + polledBox.getValue.toLong
    }
    logger.info(s"Collected ${boxesCollected.length} boxes for total value of ${currentSum}")
    boxesCollected
  }
  object PlacementFunctions {

    def executePlacement(): Unit = {
      implicit val timeout: Timeout = Timeout(60 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      val blockResp = quickQuery ? BlocksByStatus(Block.CONFIRMED)
      // TODO: Change pending block num to group exec num
      logger.info(s"Querying blocks with confirmed status")
      val blocks = Await.result(blockResp.mapTo[Seq[Block]], 10 seconds).take(params.pendingBlockNum * 2)
      if(blocks.nonEmpty) {
        val selectedBlocks = selectBlocks(blocks, !params.parallelPoolPlacements)
        val blockBoxMap = collectHoldingInputs(selectedBlocks)
        val holdingComponents = constructHoldingComponents(selectedBlocks)
        holdingComponents.onComplete {
          case Success(components) =>
            val executions = components.map {
              holdingComp =>
                val inputBoxes = blockBoxMap(holdingComp.block.blockheight)

                logger.info(s"Using input boxes with values: ${inputBoxes.map(b => b.getValue.toLong).mkString}")

                holdingComp.builder.inputBoxes = Some(inputBoxes)
                val holdResponse = (groupHandler ? ExecuteHolding(holdingComp)).mapTo[HoldingResponse]
                evalHoldingResponse(holdResponse)
            }
            val allComplete = Future.sequence(executions)
            allComplete.onComplete{
              case Success(value) =>
                logger.info("All placement executions completed, now initiating distribution!")
                DistributionFunctions.executeDistribution()
              case Failure(exception) =>
                logger.warn("Placement executions failed! Now initiating distribution.")
                DistributionFunctions.executeDistribution()
            }
          case Failure(exception) =>
            logger.error("There was an error collecting holding components!", exception)
        }
      }else{
        logger.info("No confirmed blocks found for placements, now exiting placement execution.")
      }
    }

    def evalHoldingResponse(holdingResponse: Future[HoldingResponse]): Future[HoldingResponse] = {
      implicit val timeout: Timeout = Timeout(60 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      holdingResponse.onComplete{
        case Success(response) =>
          if(response.nextPlacements.nonEmpty) {
            logger.info(s"Holding execution was success for block ${response.block.blockheight} and pool ${response.nextPlacements.head}")
            logger.info("Now updating block to processing status and inserting placements into placements table")
            val blockUpdate = (slowWrite ? UpdateBlockStatus(Block.PROCESSING, response.block.blockheight)).mapTo[Long]
            val placeInsertion = (slowWrite ? InsertPlacements(response.nextPlacements.head.subpool, response.nextPlacements)).mapTo[Long]
            val rowsUpdated = for{
              blockRowsUpdated <- blockUpdate
              placeRowsInserted <- placeInsertion
            } yield {
              if(blockRowsUpdated > 0)
                logger.info(s"${blockRowsUpdated} rows were updated for block ${response.block.blockheight}")
              else
                logger.error(s"No rows were updated for block ${response.block.blockheight}!")
              if(placeRowsInserted > 0)
                logger.info(s"${placeRowsInserted} rows were inserted for placements for pool ${response.nextPlacements.head.subpool}")
              else
                logger.error(s"No placements were inserted for pool ${response.nextPlacements.head.subpool}")
            }
          }
        case Failure(exception) =>
          logger.error("A fatal error occurred during evaluation of a holding response!", exception)
      }
      holdingResponse
    }

    def constructHoldingComponents(blockSelections: Seq[BlockSelections]): Future[Seq[HoldingComponents]] = {
      implicit val timeout: Timeout = Timeout(120 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      val collectedComponents = blockSelections.map {
        blockSel =>
          val poolTag = blockSel.poolTag
          val block = blockSel.block
          val poolStateResp = (quickQuery ? QueryAllSubPools(poolTag)).mapTo[Seq[PoolState]]
          val poolMinersResp = (quickQuery ? MinersByAssignedPool(poolTag)).mapTo[Seq[MinerSettings]]
          val poolSharesResp = (quickQuery ? QueryWithShareHandler(PaymentType.PPLNS_WINDOW, block.blockheight)).mapTo[ShareCollector]
          val fPoolInfo      = (quickQuery ? QueryPoolInfo(poolTag)).mapTo[PoolInformation]
          val holdingComponents = for{
            poolStates <- poolStateResp
            minerSettings <- poolMinersResp
            collector     <- poolSharesResp
            poolInfo      <- fPoolInfo
          } yield modifyHoldingData(poolStates, minerSettings, collector, poolInfo, blockSel)
          holdingComponents.flatten
      }
      Future.sequence(collectedComponents)
    }

    // TODO: Current parallelized implementation works well for ensuring multiple groups are executed,
    // TODO: But does not take into account that failure during box collection will cause a fatal error for all groups
    // TODO: In the future, ensure failure of one group does not affect others
    def collectHoldingInputs(blockSelections: Seq[BlockSelections]): Map[Long, Seq[InputBox]] = {
      var blockBoxMap = Map.empty[Long, Seq[InputBox]]
      // Make this a future
      for(blockSel <- blockSelections) {
        blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> collectFromLoaded(HoldingRoot.getMaxInputs(blockSel.block.getErgReward)))
      }
      blockBoxMap
    }

    def modifyHoldingData(poolStates: Seq[PoolState], minerSettings: Seq[MinerSettings], collector: ShareCollector, poolInformation: PoolInformation, blockSel: BlockSelections): Future[HoldingComponents] = {
      implicit val timeout: Timeout = Timeout(15 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      collector.shareMap.retain((m, s) => minerSettings.exists(ms => ms.address == m))

      logger.info(s"Collector shareMap length: ${collector.shareMap.size}")
      logger.info(s"shareMap: ${collector.shareMap.toString()}")
      // TODO: Make pool flags (1) equal to share operator payment type

      // Collect new min payments for this placement
      val members = collector.toMembers.map{
        m => m.copy( memberInfo =
          m.memberInfo.withMinPay(
            (minerSettings.find(s => s.address == m.address.toString).get.paymentthreshold * BigDecimal(Parameters.OneErg)).longValue()
          )
        )
      }

      logger.info("Num members: " + members.length)
      val lastPlacementResp = (quickQuery ? QueryLastPlacement(blockSel.poolTag)).mapTo[Option[PoolPlacement]]
      lastPlacementResp.flatMap {
        case Some(placement) =>
          logger.info(s"Last placement was found for pool ${placement.subpool} at block ${placement.block}")
          val placementsByBlock = (quickQuery ? PlacementsByBlock(blockSel.poolTag, placement.block)).mapTo[Seq[PoolPlacement]]
          placementsByBlock.flatMap{
            blockPlacements =>
              (groupHandler ? ConstructHolding(blockSel.poolTag, poolStates,
                members, Some(blockPlacements), poolInformation, blockSel.block)).mapTo[HoldingComponents]
          }
        case None =>
          logger.warn(s"No last placement was found for pool ${blockSel.poolTag} and block ${blockSel.block.blockheight} ")
          (groupHandler ? ConstructHolding(blockSel.poolTag, poolStates,
            members, None, poolInformation, blockSel.block)).mapTo[HoldingComponents]
      }
    }

  }

  object DistributionFunctions {


    def executeDistribution(): Unit = {
      implicit val timeout: Timeout = Timeout(60 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      val blockResp = quickQuery ? BlocksByStatus(Block.PROCESSING)
      // TODO: Change pending block num to group exec num
      logger.info(s"Querying blocks with processing status")
      val blocks = Await.result(blockResp.mapTo[Seq[Block]], 10 seconds).take(params.pendingBlockNum * 2)
      if(blocks.nonEmpty) {
        val selectedBlocks = selectBlocks(blocks, distinctOnly = true)
        val blockBoxMap = collectDistributionInputs(selectedBlocks)
        val collectedComponents = constructDistComponents(selectedBlocks)

        collectedComponents.onComplete {
          case Success(components) =>
            val executions = Future.sequence(components.map {
              dc =>
                val inputBoxes = blockBoxMap(dc.block.blockheight)
                dc.builder.inputBoxes = Some(inputBoxes)
                val distResponse = (groupHandler ? ExecuteDistribution(dc, dc.block)).mapTo[DistributionResponse]
                evalDistributionResponse(distResponse)
            })

          case Failure(exception) =>
            logger.error("There was an error collecting distribution components!", exception)
        }
      }else{
        logger.info("No processing blocks found, now exiting distribution execution")
      }
    }

    def evalDistributionResponse(distResponse: Future[DistributionResponse]): Future[DistributionResponse] = {
      implicit val timeout: Timeout = Timeout(60 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      distResponse.onComplete {
        case Success(dr) =>
          if (dr.nextStates.isEmpty || dr.nextMembers.isEmpty) {
            logger.error("There was a fatal error during distribution execution, response returned empty")
            logger.error("No updates being made")
          } else {
            var nextStates = dr.nextStates
            if(params.autoConfirmGroups){
              logger.info("Auto confirm groups is on, setting pool states to confirmed and blocks to paid")
              nextStates = dr.nextStates.map(s => s.copy(status = PoolState.CONFIRMED))
              slowWrite ! UpdateBlockStatus(Block.PAID, dr.nextStates.head.block)
              logger.info(s"Now deleting placements for block ${dr.nextStates.head.block}")
              slowWrite ! DeletePlacementsAtBlock(dr.nextStates.head.subpool, dr.nextStates.head.block)
            }

            val updateStatesReq = slowWrite ? UpdateWithNewStates(nextStates)
            updateStatesReq.mapTo[Long].onComplete {
              case Success(rows) =>
                if (rows > 0) {
                  logger.info(s"$rows were updated successfully!")
                } else {
                  logger.error("0 rows were updated!")
                }
              case Failure(exception) =>
                logger.error(s"There was an error updating the pool states for pool ${dr.nextStates.head.subpool}", exception)
            }
            val insertMembersReq = slowWrite ? InsertMembers(dr.nextStates.head.subpool, dr.nextMembers)
            insertMembersReq.mapTo[Long].onComplete {
              case Success(rows) =>
                if (rows > 0) {
                  logger.info(s"$rows in the members table were updated successfully!")
                } else {
                  logger.error("0 rows were updated!")
                }
              case Failure(exception) =>
                logger.error(s"There was an error inserting members for pool ${dr.nextStates.head.subpool}", exception)
            }
            val nextgEpoch = dr.nextMembers.head.g_epoch
            val gEpochUpdate = slowWrite ? UpdatePoolGEpoch(dr.nextStates.head.subpool, nextgEpoch)
            gEpochUpdate.mapTo[Long].onComplete {
              case Success(rows) =>
                if (rows > 0) {
                  logger.info(s"$rows in the states table were updated to have new gEpoch $nextgEpoch")
                } else {
                  logger.error("0 rows were updated!")
                }
              case Failure(exception) =>
                logger.error(s"There was an error updating the gEpoch for pool ${dr.nextStates.head.subpool}", exception)
            }
          }
        case Failure(exception) =>
          logger.error("There was a fatal error while evaluating a distribution response!", exception)

      }
      distResponse
    }

    def constructDistComponents(blockSelections: Seq[BlockSelections]): Future[Seq[DistributionComponents]] = {
      implicit val timeout: Timeout = Timeout(35 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      val collectedComponents = blockSelections.map {
        blockSel =>
          val poolTag = blockSel.poolTag
          val block = blockSel.block
          val fPoolInformation = (quickQuery ? QueryPoolInfo(poolTag)).mapTo[PoolInformation]
          val poolStateResp = (quickQuery ? QueryAllSubPools(poolTag)).mapTo[Seq[PoolState]]
          val fPlacements = (quickQuery ? PlacementsByBlock(poolTag, block.blockheight)).mapTo[Seq[PoolPlacement]]

          val distComponents = for{
            states <- poolStateResp
            placements <- fPlacements
            poolInfo <- fPoolInformation
          } yield {

              val gEpoch = states.head.g_epoch
              // TODO: Make these trys in order to prevent whole group failure when multiple groups from same pool are used

              val constructDistResp = {
                require(placements.nonEmpty, s"No placements found for block ${block.blockheight}")
                require(placements.head.g_epoch == gEpoch + 1, "gEpoch was incorrect for these placements, maybe this is a future placement?")
                groupHandler ? ConstructDistribution(poolTag, states, placements, poolInfo, block)
              }

              constructDistResp.map {
                case constructionResponse: DistributionComponents =>
                  constructionResponse
                case failedPlacements: FailedPlacements =>
                  logger.warn(s"Placements were failed for pool ${poolTag} at block ${failedPlacements.block.blockheight}")
                  val lastPlacements = (quickQuery ? QueryLastPlacement(poolTag)).mapTo[Option[PoolPlacement]]
                  lastPlacements.onComplete {
                    case Success(optPlacement) =>
                      optPlacement match {
                        case Some(placed) =>
                          if(placed.block == failedPlacements.block.blockheight){
                            logger.info("Last placement and failed placement were the same, no changes will be made.")
                            // TODO: Refine this so that it doesn't cause issues for low hashrate pools
                          }else{
                            logger.warn("Last placement and failed placements were not the same, now deleting placements at block " +
                              "and setting blocks status to confirmed again!")
                            slowWrite ! DeletePlacementsAtBlock(poolTag, failedPlacements.block.blockheight)
                            slowWrite ! UpdateBlockStatus(Block.CONFIRMED, failedPlacements.block.blockheight)
                          }
                        case None =>
                          logger.error("This path should never be executed, something went wrong!")
                      }

                    case Failure(exception) =>
                      logger.error("There was an error while attempting to get last placements. No changes will be made to the database",
                        exception)
                  }

                  throw new Exception("Placements failed!")
                case _ =>
                  logger.error("There was a fatal error during Distribution Construction")
                  throw new Exception("An unexpected type was returned during Distribution Construction!")
              }
          }
          distComponents.flatten
      }
      Future.sequence(collectedComponents)
    }



    // TODO: Current parallelized implementation works well for ensuring multiple groups are executed,
    // TODO: But does not take into account that failure during box collection will cause a fatal error for all groups
    // TODO: In the future, ensure failure of one group does not affect others
    def collectDistributionInputs(blockSelections: Seq[BlockSelections]): Map[Long, Seq[InputBox]] = {
      var blockBoxMap = Map.empty[Long, Seq[InputBox]]

      for(blockSel <- blockSelections){
        blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> collectFromLoaded(DistributionRoot.getMaxInputs))
      }

      blockBoxMap
    }

  }

}
