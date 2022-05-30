package utils

import actors.BlockingDbWriter._
import actors.ExplorerRequestBus.ExplorerRequests.GetCurrentHeight
import actors.GroupRequestHandler._
import actors.QuickDbReader.{PlacementsByBlock, PoolBlocksByStatus, QueryAllSubPools, QueryLastPlacement}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.groups.stages.roots.DistributionRoot
import io.getblok.subpooling_core.persistence.models.Models.{PoolBlock, PoolPlacement, PoolState}
import org.ergoplatform.appkit.InputBox
import org.slf4j.{Logger, LoggerFactory}
import utils.ConcurrentBoxLoader.BlockSelection

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class DistributionFunctions(query: ActorRef, write: ActorRef, expReq: ActorRef, groupHandler: ActorRef,
                            contexts: Contexts, params: ParamsConfig, taskConf: TaskConfiguration,
                            boxLoader: ConcurrentBoxLoader) {
  val logger: Logger = LoggerFactory.getLogger("DistributionFunctions")


  def executeDistribution(): Unit = {
    implicit val timeout: Timeout = Timeout(120 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val blockResp = query ? PoolBlocksByStatus(PoolBlock.PROCESSED)
    // TODO: Change pending block num to group exec num
    logger.info(s"Querying blocks with processing status")
    val blocks = Await.result(blockResp.mapTo[Seq[PoolBlock]], 100 seconds).take(params.pendingBlockNum * 2)
    if(blocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(blocks, distinctOnly = true)
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
  // TODO: MUST CHANGE
  def evalDistributionResponse(distResponse: Future[DistributionResponse]): Future[DistributionResponse] = {
    implicit val timeout: Timeout = Timeout(80 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    distResponse.onComplete {
      case Success(dr) =>
        if (dr.nextStates.isEmpty || dr.nextMembers.isEmpty) {
          logger.error("There was a fatal error during distribution execution, response returned empty")
          logger.error("No updates being made")
        } else {
          var nextStates = dr.nextStates.map(_.copy(g_epoch = dr.block.gEpoch))
          if(params.autoConfirmGroups){
            logger.info("Auto confirm groups is on, setting pool states to confirmed and blocks to paid")
            nextStates = dr.nextStates.map(s => s.copy(status = PoolState.CONFIRMED))
            write ! UpdatePoolBlockStatus(PoolBlock.PAID, dr.nextStates.head.block)
            logger.info(s"Now deleting placements for block ${dr.nextStates.head.block}")
            write ! DeletePlacementsAtBlock(dr.nextStates.head.subpool, dr.nextStates.head.block)
          }

          val updateStatesReq = write ? UpdateWithNewStates(nextStates)
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
          val insertMembersReq = write ? InsertMembers(dr.nextStates.head.subpool, dr.nextMembers.map(_.copy(g_epoch = dr.block.gEpoch)))
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
          val gEpochUpdate = write ? UpdatePoolGEpoch(dr.block.poolTag, dr.block.gEpoch)
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

          write ! UpdatePoolBlockStatus(PoolBlock.INITIATED, dr.block.blockheight)
        }
      case Failure(exception) =>
        logger.error("There was a fatal error while evaluating a distribution response!", exception)

    }
    distResponse
  }

  def constructDistComponents(blockSelections: Seq[BlockSelection]): Future[Seq[DistributionComponents]] = {
    implicit val timeout: Timeout = Timeout(100 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = blockSelections.map {
      blockSel =>
        val poolTag = blockSel.poolInformation.poolTag
        val poolInfo = blockSel.poolInformation
        val block = blockSel.block
        val poolStateResp = (query ? QueryAllSubPools(poolTag)).mapTo[Seq[PoolState]]
        val fPlacements = (query ? PlacementsByBlock(poolTag, block.blockheight)).mapTo[Seq[PoolPlacement]]
        val futHeight   = (expReq ? GetCurrentHeight).mapTo[Int]
        val distComponents = for{
          states <- poolStateResp
          placements <- fPlacements
          height    <- futHeight
        } yield {
          // TODO: Removed for right now, exercise caution
          val gEpoch = poolInfo.g_epoch

          // TODO: Make these trys in order to prevent whole group failure when multiple groups from same pool are used

          val constructDistResp = {
            require(placements.nonEmpty, s"No placements found for block ${block.blockheight}")
            logger.info(s"Construction distributions for block ${block.blockheight}")
            logger.info(s"Placements gEpoch: ${placements.head.g_epoch}, block: ${block.gEpoch}, poolInfo gEpoch: ${gEpoch}")
            require(placements.head.g_epoch == block.gEpoch, "gEpoch was incorrect for these placements, maybe this is a future placement?")
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
                            write ! DeletePlacementsAtBlock(poolTag, failedPlacements.block.blockheight)
                            write ! UpdatePoolBlockStatus(PoolBlock.CONFIRMED, failedPlacements.block.blockheight)
                          }else{
                            logger.warn(s"Current height $height is not large enough to restart placements")
                          }
                        } else {
                          logger.warn("Last placement and failed placements were not the same, now deleting placements at block " +
                            "and setting blocks status to confirmed again!")
                          write ! DeletePlacementsAtBlock(poolTag, failedPlacements.block.blockheight)
                          write ! UpdatePoolBlockStatus(PoolBlock.CONFIRMED, failedPlacements.block.blockheight)
                        }
                      case None =>
                        logger.error("This path should never be executed, something went wrong!")
                    }

                  case Failure(exception) =>
                    logger.error("There was an error while attempting to get last placements. No changes will be made to the database",
                      exception)
                }
              }else{
                logger.warn("Failed placements were found, now setting block back to processing status to have DbCrossCheck re-evaluate")
                write ! UpdatePoolBlockStatus(PoolBlock.PROCESSING, failedPlacements.block.blockheight)
              }
              logger.error("There was a fatal error during distribution due to invalid placements!")
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
      blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> boxLoader.collectFromLoaded(DistributionRoot.getMaxInputs))
    }

    blockBoxMap
  }

}
