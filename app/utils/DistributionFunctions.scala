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
import models.DatabaseModels.SPoolBlock
import org.ergoplatform.appkit.InputBox
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader.BlockSelection

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class DistributionFunctions(query: ActorRef, write: ActorRef, expReq: ActorRef, groupHandler: ActorRef,
                            contexts: Contexts, params: ParamsConfig, taskConf: TaskConfiguration,
                            boxLoader: ConcurrentBoxLoader, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("DistributionFunctions")
  import slick.jdbc.PostgresProfile.api._

  def executeDistribution(): Unit = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    logger.info("Now querying processed blocks for distribution")
    val blockResp = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PROCESSED).sortBy(_.created).result)
    // TODO: Change pending block num to group exec num
    logger.info(s"Querying blocks with processed status")
    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds).take(params.pendingBlockNum * 2)
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
              logger.info("Now sending dist req")
              val distResponse = (groupHandler ? ExecuteDistribution(dc, dc.block)).mapTo[DistributionResponse]
              logger.info("Waiting to eval dist response")
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
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    distResponse.onComplete {
      case Success(dr) =>
        logger.info("Now evaluating dist response")
        if (dr.nextStates.isEmpty || dr.nextMembers.isEmpty) {
          logger.error("There was a fatal error during distribution execution, response returned empty")
          logger.error("No updates being made")
        } else {
          var nextStates = dr.nextStates.map(_.copy(g_epoch = dr.block.gEpoch))

          // Did not edit this to use slick db, re-evaluate later
          if(params.autoConfirmGroups){
            logger.info("Auto confirm groups is on, setting pool states to confirmed and blocks to paid")
            nextStates = dr.nextStates.map(s => s.copy(status = PoolState.CONFIRMED))
            write ! UpdatePoolBlockStatus(PoolBlock.PAID, dr.nextStates.head.block)
            logger.info(s"Now deleting placements for block ${dr.nextStates.head.block}")
            write ! DeletePlacementsAtBlock(dr.nextStates.head.subpool, dr.nextStates.head.block)
          }

          val stateUpdates = nextStates.map{
            ns =>
              db.run(Tables.PoolStatesTable.filter(s => s.subpool === dr.block.poolTag && s.subpool_id === ns.subpool_id).map{
                s => (s.tx, s.epoch, s.height, s.status, s.members, s.block, s.updated)
              }.update(ns.tx, ns.epoch, ns.height, ns.status, ns.members, ns.block, LocalDateTime.now()))
          }.toSeq

          Future.sequence(stateUpdates).onComplete {
            case Success(rows) =>
              if (rows.sum > 0) {
                logger.info(s"${rows.sum} were updated successfully!")
              } else {
                logger.error("0 rows were updated!")
              }
            case Failure(exception) =>
              logger.error(s"There was an error updating the pool states for pool ${dr.nextStates.head.subpool}", exception)
          }
          logger.info("Now incrementing gEpoch for all states")
          db.run(Tables.PoolStatesTable.filter(s => s.subpool === dr.block.poolTag).map(_.gEpoch).update(dr.block.gEpoch))

          val insertMembersReq = db.run(Tables.SubPoolMembers ++= dr.nextMembers.map(_.copy(g_epoch = dr.block.gEpoch)))
          insertMembersReq.onComplete {
            case Success(rows) =>
              if (rows.getOrElse(0) > 0) {
                logger.info(s"$rows in the members table were updated successfully!")
              } else {
                logger.error("0 rows were updated!")
              }
            case Failure(exception) =>
              logger.error(s"There was an error inserting members for pool ${dr.nextStates.head.subpool}", exception)
          }
         // val nextgEpoch = dr.nextMembers.head.g_epoch
          val gEpochUpdate = db.run(Tables.PoolInfoTable.filter(_.poolTag === dr.block.poolTag).map(i => i.gEpoch -> i.updated)
            .update(dr.block.gEpoch -> LocalDateTime.now()))
          gEpochUpdate.onComplete {
            case Success(rows) =>
              if (rows > 0) {
                logger.info(s"$rows in the states table were updated to have new gEpoch ${dr.block.gEpoch}")
              } else {
                logger.error("0 rows were updated!")
              }
            case Failure(exception) =>
              logger.error(s"There was an error updating the gEpoch for pool ${dr.nextStates.head.subpool}", exception)
          }

          db.run(Tables.PoolBlocksTable.filter(_.blockHeight === dr.block.blockheight).map(b => b.status -> b.updated)
            .update(PoolBlock.INITIATED -> LocalDateTime.now()))
        }
      case Failure(exception) =>
        logger.error("There was a fatal error while evaluating a distribution response!", exception)

    }
    distResponse
  }

  def constructDistComponents(blockSelections: Seq[BlockSelection]): Future[Seq[DistributionComponents]] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = blockSelections.map {
      blockSel =>
        val poolTag = blockSel.poolInformation.poolTag
        val poolInfo = blockSel.poolInformation
        val block = blockSel.block
        val poolStateResp = (db.run(Tables.PoolStatesTable.filter(_.subpool === poolTag).result)).mapTo[Seq[PoolState]]
        val fPlacements = (db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag && p.block === block.blockheight).result)).mapTo[Seq[PoolPlacement]]
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
            if(placements.isEmpty){
              logger.error("Placements were empty for this block! Setting back to confirmed.")
              db.run(Tables.PoolBlocksTable.filter(_.blockHeight === block.blockheight).map(_.status).update(PoolBlock.CONFIRMED))
            }
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
                val lastPlacements = (db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag).sortBy(_.block.desc).result.headOption))
                  .mapTo[Option[PoolPlacement]]
                lastPlacements.onComplete {
                  case Success(optPlacement) =>
                    optPlacement match {
                      case Some(placed) =>
                        if (placed.block == failedPlacements.block.blockheight) {
                          logger.info("Last placement and failed placement are the same")
                          if (height - placed.block > 100){
                            logger.warn("Last placement and failed placements were not the same, now deleting placements at block " +
                              "and setting blocks status to confirmed again!")
                            db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag && p.block === failedPlacements.block.blockheight).delete)
                            db.run(Tables.PoolBlocksTable.filter(_.blockHeight === failedPlacements.block.blockheight).map(b => b.status -> b.updated)
                              .update(PoolBlock.CONFIRMED -> LocalDateTime.now()))
                          }else{
                            logger.warn(s"Current height $height is not large enough to restart placements")
                          }
                        } else {
                          logger.warn("Last placement and failed placements were not the same, now deleting placements at block " +
                            "and setting blocks status to confirmed again!")
                          db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag && p.block === failedPlacements.block.blockheight).delete)
                          db.run(Tables.PoolBlocksTable.filter(_.blockHeight === failedPlacements.block.blockheight).map(b => b.status -> b.updated)
                            .update(PoolBlock.CONFIRMED -> LocalDateTime.now()))
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
                db.run(Tables.PoolBlocksTable.filter(_.blockHeight === failedPlacements.block.blockheight).map(b => b.status -> b.updated)
                  .update(PoolBlock.PROCESSING -> LocalDateTime.now()))
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
