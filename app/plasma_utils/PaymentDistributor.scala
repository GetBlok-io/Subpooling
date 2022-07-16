package plasma_utils

import actors.BlockingDbWriter._
import actors.ExplorerRequestBus.ExplorerRequests.GetCurrentHeight
import actors.GroupRequestHandler._
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.stages.roots.DistributionRoot
import io.getblok.subpooling_core.persistence.models.Models.{PoolBlock, PoolPlacement, PoolState}
import models.DatabaseModels.SPoolBlock
import org.ergoplatform.appkit.InputBox
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader
import utils.ConcurrentBoxLoader.BatchSelection

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PaymentDistributor(query: ActorRef, write: ActorRef, expReq: ActorRef, groupHandler: ActorRef,
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
    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds)
    if(blocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(blocks, strictBatch = true)
      val blockBoxMap = collectDistributionInputs(selectedBlocks)
      val collectedComponents = constructDistComponents(selectedBlocks)

      collectedComponents.onComplete {
        case Success(components) =>
          val executions = {

            val inputBoxes = blockBoxMap(selectedBlocks)
            components.builder.inputBoxes = Some(inputBoxes)
            logger.info("Now sending dist req")
            val distResponse = (groupHandler ? ExecuteDistribution(components, components.block)).mapTo[DistributionResponse]
            logger.info("Waiting to eval dist response")
            evalDistributionResponse(distResponse)
          }

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
          val fInfo = db.run(Tables.PoolInfoTable.filter(_.poolTag === dr.block.poolTag).result.head)

          db.run(Tables.PoolBlocksTable
            .filter(b => b.poolTag === dr.block.poolTag)
            .filter(b => b.gEpoch >= dr.block.gEpoch && b.gEpoch < dr.block.gEpoch + ConcurrentBoxLoader.BLOCK_BATCH_SIZE)
            .map(b => b.status -> b.updated)
            .update(PoolBlock.INITIATED -> LocalDateTime.now()))
          logger.info(s"Finished updating blocks ${dr.block.blockheight} with epoch ${dr.block.gEpoch} and its next 4 epochs for pool" +
            s" ${dr.block.poolTag} and status INITIATED")

        }
      case Failure(exception) =>
        logger.error("There was a fatal error while evaluating a distribution response!", exception)

    }
    distResponse
  }

  def constructDistComponents(batchSelection: BatchSelection): Future[DistributionComponents] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = {
      val poolTag = batchSelection.info.poolTag
      val poolInfo = batchSelection.info
      val block = batchSelection.blocks.head
      val poolStateResp = (db.run(Tables.PoolStatesTable.filter(_.subpool === poolTag).result)).mapTo[Seq[PoolState]]
      val fPlacements = (db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag && p.block === block.blockheight).result)).mapTo[Seq[PoolPlacement]]
      val futHeight   = (expReq ? GetCurrentHeight).mapTo[Int]
      val initBlocks = Await.result((db.run(Tables.PoolBlocksTable.filter(b => b.status === PoolBlock.INITIATED && b.poolTag === block.poolTag).result)), 100 seconds)
      require(initBlocks.isEmpty, s"Initiated batches already exist for pool ${poolTag}!")
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
          logger.info(s"Current epochs in batch: ${batchSelection.blocks.map(_.gEpoch).toArray.mkString("Array(", ", ", ")")}")
          logger.info(s"Current blocks in batch: ${batchSelection.blocks.map(_.blockheight).toArray.mkString("Array(", ", ", ")")}")
          require(placements.head.g_epoch == block.gEpoch, "gEpoch was incorrect for these placements, maybe this is a future placement?")
          groupHandler ? ConstructDistribution(poolTag, states, placements, poolInfo, block)
        }

        constructDistResp.map {
          case constructionResponse: DistributionComponents =>
            constructionResponse
          case failedPlacements: FailedPlacements =>
            logger.warn(s"Placements were failed for pool ${poolTag} at block ${failedPlacements.block.blockheight}")


            logger.error("There was a fatal error during distribution due to invalid placements!")
            throw new Exception("Placements failed!")
          case _ =>
            logger.error("There was a fatal error during Distribution Construction")
            throw new Exception("An unexpected type was returned during Distribution Construction!")
        }
      }
      distComponents.flatten
    }
    collectedComponents
  }


  // TODO: Currently vertically scaled, consider horizontal scaling with Seq[BatchSelections]
  def collectDistributionInputs(batchSelection: BatchSelection): Map[BatchSelection, Seq[InputBox]] = {
    val blockSum = Helpers.ergToNanoErg(batchSelection.blocks.map(_.reward).sum) + (Helpers.OneErg * 2)
    Map(batchSelection -> boxLoader.collectFromLoaded(blockSum))
  }

}
