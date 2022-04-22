package tasks

import actors.BlockingDbWriter.{DeletePlacementsAtBlock, InsertMembers, InsertPlacements, UpdateBlockStatus, UpdatePoolBlockConf, UpdatePoolBlockStatus, UpdatePoolGEpoch, UpdateWithNewStates, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{BlockByHash, GetCurrentHeight, ValidateBlockByHeight}
import actors.GroupRequestHandler.{ConstructDistribution, ConstructHolding, DistributionComponents, DistributionResponse, ExecuteDistribution, ExecuteHolding, FailedPlacements, HoldingComponents, HoldingResponse}
import actors.QuickDbReader.{BlockByHeight, MinersByAssignedPool, PlacementsByBlock, PoolBlocksByStatus, QueryAllSubPools, QueryLastPlacement, QueryPoolInfo, QueryWithShareHandler, SettingsForMiner}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.explorer.Models.BlockContainer
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups.stages.roots.{DistributionRoot, EmissionRoot, HoldingRoot}
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.payments.ShareCollector
import io.getblok.subpooling_core.persistence.models.Models.{Block, MinerSettings, PoolBlock, PoolInformation, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BoxOperations, ErgoClient, ErgoId, InputBox, Parameters}
import org.ergoplatform.wallet.boxes.BoxSelector
import play.api.{Configuration, Logger}

import java.time.{LocalDateTime, ZoneOffset}
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
                                   @Named("quick-db-reader") query: ActorRef, @Named("blocking-db-writer") slowWrite: ActorRef,
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
  case class PartialBlockSelection(block: PoolBlock, poolTag: String)
  case class BlockSelection(block: PoolBlock, poolInformation: PoolInformation)


  def selectBlocks(blocks: Seq[PoolBlock], distinctOnly: Boolean): Seq[BlockSelection] = {
    implicit val timeout: Timeout = Timeout(20 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    logger.info("Now selecting blocks with unique pool tags")
    val selectedBlocks = blocks.map {
      block =>
        logger.info(s"Getting pool for block ${block.blockheight} with miner ${block.miner}")
        val settingsQuery = Await.result((query ? SettingsForMiner(block.miner)).mapTo[MinerSettings].map(s => s.subpool), timeout.duration)
        PartialBlockSelection(block, settingsQuery)
    }
    if(distinctOnly) {
      val distinctBlocks = ArrayBuffer.empty[PartialBlockSelection]
      for (blockSel <- selectedBlocks) {
        if (!distinctBlocks.exists(bs => bs.poolTag == blockSel.poolTag)) {
          logger.info(s"Unique pool tag ${blockSel.poolTag} was added to selection!")
          distinctBlocks += blockSel
        }
      }
      distinctBlocks.toSeq.take(params.pendingBlockNum)
        .map(pb => BlockSelection(pb.block, Await.result((query ? QueryPoolInfo(pb.poolTag)).mapTo[PoolInformation], timeout.duration)))
    }else{
      selectedBlocks.take(params.pendingBlockNum)
        .map(pb => BlockSelection(pb.block, Await.result((query ? QueryPoolInfo(pb.poolTag)).mapTo[PoolInformation], timeout.duration)))
    }
  }

  def makeBlockBoxMap(blockSelections: Seq[BlockSelection], collectedInputs: ArrayBuffer[InputBox], maxInputs: Long): Map[Long, Seq[InputBox]] = {
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
      val blockResp = query ? PoolBlocksByStatus(PoolBlock.CONFIRMED)
      // TODO: Change pending block num to group exec num
      logger.info(s"Querying blocks with confirmed status")
      val blocks = Await.result(blockResp.mapTo[Seq[PoolBlock]], 10 seconds).take(params.pendingBlockNum * 2)
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
                holdingComp.root match {
                  case root: HoldingRoot =>
                    root.inputBoxes = Some(inputBoxes)
                  case root: EmissionRoot =>
                    root.inputBoxes = Some(inputBoxes)
                }
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
            val blockUpdate = (slowWrite ? UpdatePoolBlockStatus(PoolBlock.PROCESSING, response.block.blockheight)).mapTo[Long]
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

    def constructHoldingComponents(blockSelections: Seq[BlockSelection]): Future[Seq[HoldingComponents]] = {
      implicit val timeout: Timeout = Timeout(120 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      val collectedComponents = blockSelections.map {
        blockSel =>
          val poolTag = blockSel.poolInformation.poolTag
          val block = blockSel.block
          val poolStateResp = (query ? QueryAllSubPools(poolTag)).mapTo[Seq[PoolState]]
          val poolMinersResp = (query ? MinersByAssignedPool(poolTag)).mapTo[Seq[MinerSettings]]
          val poolSharesResp = (query ? QueryWithShareHandler(PaymentType.PPLNS_WINDOW, block.blockheight)).mapTo[ShareCollector]
          val holdingComponents = for{
            poolStates <- poolStateResp
            minerSettings <- poolMinersResp
            collector     <- poolSharesResp
          } yield modifyHoldingData(poolStates, minerSettings, collector, blockSel)
          holdingComponents.flatten
      }
      Future.sequence(collectedComponents)
    }

    // TODO: Current parallelized implementation works well for ensuring multiple groups are executed,
    // TODO: But does not take into account that failure during box collection will cause a fatal error for all groups
    // TODO: In the future, ensure failure of one group does not affect others
    def collectHoldingInputs(blockSelections: Seq[BlockSelection]): Map[Long, Seq[InputBox]] = {
      var blockBoxMap = Map.empty[Long, Seq[InputBox]]
      // Make this a future
      for(blockSel <- blockSelections) {
        blockSel.poolInformation.currency match {
          case PoolInformation.CURR_ERG =>
            blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> collectFromLoaded(HoldingRoot.getMaxInputs(blockSel.block.getErgReward)))
          case PoolInformation.CURR_TEST_TOKENS =>
            blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> collectFromLoaded(EmissionRoot.getMaxInputs(blockSel.block.getErgReward)))
        }

      }
      blockBoxMap
    }

    def modifyHoldingData(poolStates: Seq[PoolState], minerSettings: Seq[MinerSettings], collector: ShareCollector, blockSel: BlockSelection): Future[HoldingComponents] = {
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
      val poolTag = blockSel.poolInformation.poolTag
      val poolInformation = blockSel.poolInformation
      logger.info("Num members: " + members.length)
      val lastPlacementResp = (query ? QueryLastPlacement(poolTag)).mapTo[Option[PoolPlacement]]
      lastPlacementResp.flatMap {
        case Some(placement) =>
          logger.info(s"Last placement was found for pool ${placement.subpool} at block ${placement.block}")
          val placementsByBlock = (query ? PlacementsByBlock(poolTag, placement.block)).mapTo[Seq[PoolPlacement]]
          placementsByBlock.flatMap{
            blockPlacements =>
              (groupHandler ? ConstructHolding(poolTag, poolStates,
                members, Some(blockPlacements), poolInformation, blockSel.block)).mapTo[HoldingComponents]
          }
        case None =>
          logger.warn(s"No last placement was found for pool ${poolTag} and block ${blockSel.block.blockheight} ")
          (groupHandler ? ConstructHolding(poolTag, poolStates,
            members, None, poolInformation, blockSel.block)).mapTo[HoldingComponents]
      }
    }

  }

  object DistributionFunctions {


    def executeDistribution(): Unit = {
      implicit val timeout: Timeout = Timeout(60 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      val blockResp = query ? PoolBlocksByStatus(PoolBlock.PROCESSING)
      // TODO: Change pending block num to group exec num
      logger.info(s"Querying blocks with processing status")
      val blocks = Await.result(blockResp.mapTo[Seq[PoolBlock]], 10 seconds).take(params.pendingBlockNum * 2)
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
              slowWrite ! UpdatePoolBlockStatus(PoolBlock.PAID, dr.nextStates.head.block)
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

    def constructDistComponents(blockSelections: Seq[BlockSelection]): Future[Seq[DistributionComponents]] = {
      implicit val timeout: Timeout = Timeout(35 seconds)
      implicit val taskContext: ExecutionContext = contexts.taskContext
      val collectedComponents = blockSelections.map {
        blockSel =>
          val poolTag = blockSel.poolInformation.poolTag
          val poolInfo = blockSel.poolInformation
          val block = blockSel.block
          val poolStateResp = (query ? QueryAllSubPools(poolTag)).mapTo[Seq[PoolState]]
          val fPlacements = (query ? PlacementsByBlock(poolTag, block.blockheight)).mapTo[Seq[PoolPlacement]]
          val futHeight   = (explorerReqBus ? GetCurrentHeight).mapTo[Int]
          val distComponents = for{
            states <- poolStateResp
            placements <- fPlacements
            height    <- futHeight
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
                  if(params.autoConfirmGroups) {
                    val lastPlacements = (query ? QueryLastPlacement(poolTag)).mapTo[Option[PoolPlacement]]
                    lastPlacements.onComplete {
                      case Success(optPlacement) =>
                        optPlacement match {
                          case Some(placed) =>
                            if (placed.block == failedPlacements.block.blockheight) {
                              logger.info("Last placement and failed placement are the same")
                              if (height - placed.block > 10){
                                logger.warn("Last placement and failed placements were not the same, now deleting placements at block " +
                                  "and setting blocks status to confirmed again!")
                                slowWrite ! DeletePlacementsAtBlock(poolTag, failedPlacements.block.blockheight)
                                slowWrite ! UpdatePoolBlockStatus(PoolBlock.CONFIRMED, failedPlacements.block.blockheight)
                              }else{
                                logger.warn(s"Current height $height is not large enough to restart placements")
                              }
                            } else {
                              logger.warn("Last placement and failed placements were not the same, now deleting placements at block " +
                                "and setting blocks status to confirmed again!")
                              slowWrite ! DeletePlacementsAtBlock(poolTag, failedPlacements.block.blockheight)
                              slowWrite ! UpdatePoolBlockStatus(PoolBlock.CONFIRMED, failedPlacements.block.blockheight)
                            }
                          case None =>
                            logger.error("This path should never be executed, something went wrong!")
                        }

                      case Failure(exception) =>
                        logger.error("There was an error while attempting to get last placements. No changes will be made to the database",
                          exception)
                    }
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
    def collectDistributionInputs(blockSelections: Seq[BlockSelection]): Map[Long, Seq[InputBox]] = {
      var blockBoxMap = Map.empty[Long, Seq[InputBox]]

      for(blockSel <- blockSelections){
        blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> collectFromLoaded(DistributionRoot.getMaxInputs))
      }

      blockBoxMap
    }

  }

}
