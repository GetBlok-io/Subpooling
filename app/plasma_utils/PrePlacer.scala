package plasma_utils

import actors.ExplorerRequestBus.ExplorerRequests.BoxesByTokenId
import actors.GroupRequestHandler.{ConstructHolding, ExecuteHolding, HoldingComponents, HoldingResponse}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig}
import io.getblok.subpooling_core.explorer.Models.Output
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.Models._
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.ergoplatform.appkit.InputBox
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import persistence.shares.{ShareCollector, ShareHandler}
import plasma_utils.payments.PaymentRouter
import plasma_utils.shares.BatchShareCollector
import slick.jdbc.PostgresProfile
import utils.{ConcurrentBoxLoader, PoolTemplates}
import utils.ConcurrentBoxLoader.BatchSelection

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PrePlacer(contexts: Contexts, params: ParamsConfig,
                nodeConfig: NodeConfig, boxLoader: ConcurrentBoxLoader, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("PrePlacer")
  import slick.jdbc.PostgresProfile.api._
  implicit val timeout: Timeout = Timeout(1000 seconds)
  implicit val taskContext: ExecutionContext = contexts.taskContext

  case class PrePlacement(placements: Seq[PoolPlacement], batchSelection: BatchSelection)

  def preparePlacements(): Unit = {

    val blockResp = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.CONFIRMED).sortBy(_.created).result)
    val infoResp = db.run(Tables.PoolInfoTable.result)

    logger.info(s"Querying blocks with confirmed status")
    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds)
    val infos = Await.result(infoResp, 1000 seconds)

    val plasmaBlocks = PaymentRouter.routePlasmaBlocks(blocks, infos, routePlasma = true)

    if(plasmaBlocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(plasmaBlocks, strictBatch = true)
      val prePlacement = collectShares(selectedBlocks)
      writePrePlacement(prePlacement)
    }
  }

  def writePrePlacement(futPrePlacement: Future[PrePlacement]): Unit = {

    futPrePlacement.onComplete{
      case Success(response) =>
        if(response.placements.nonEmpty) {
          logger.info(s"Holding execution was success for batch starting with ${response.batchSelection.blocks.head.blockheight} and pool ${response.placements.head}")
          logger.info("Now updating batched blocks to processing status and inserting placements into placements table")

          val blockUpdate = {
            if(response.batchSelection.info.currency == PoolInformation.CURR_ERG) {
              // We auto set ERG only pools to processed, due to not needing any emission contract
              db.run(Tables.PoolBlocksTable
                .filter(_.poolTag === response.batchSelection.blocks.head.poolTag)
                .filter(_.gEpoch >= response.batchSelection.blocks.head.gEpoch)
                .filter(_.gEpoch <= response.batchSelection.blocks.last.gEpoch)
                .map(b => b.status -> b.updated)
                .update(PoolBlock.PROCESSED -> LocalDateTime.now()))
            }else{
              db.run(Tables.PoolBlocksTable
                .filter(_.poolTag === response.batchSelection.blocks.head.poolTag)
                .filter(_.gEpoch >= response.batchSelection.blocks.head.gEpoch)
                .filter(_.gEpoch <= response.batchSelection.blocks.last.gEpoch)
                .map(b => b.status -> b.updated)
                .update(PoolBlock.PRE_PROCESSED -> LocalDateTime.now()))
            }
          }

          val placeInsertion = db.run(Tables.PoolPlacementsTable ++= response.placements.toSeq)
          val rowsUpdated = for{
            blockRowsUpdated <- blockUpdate
            placeRowsInserted <- placeInsertion
          } yield {
            if(blockRowsUpdated > 0)
              logger.info(s"${blockRowsUpdated} rows were updated for ${response.batchSelection.blocks.length} blocks")
            else
              logger.error(s"No rows were updated for ${response.batchSelection.blocks.length} blocks!")
            if(placeRowsInserted.getOrElse(0) > 0)
              logger.info(s"${placeRowsInserted} rows were inserted for placements for pool ${response.placements.head.subpool}")
            else
              logger.error(s"No placements were inserted for pool ${response.placements.head.subpool}")
          }

        }
      case Failure(exception) =>
        logger.error("A fatal error occurred during evaluation of a pre placement!", exception)
    }

  }

  def collectShares(batchSelection: BatchSelection): Future[PrePlacement] = {
    val collectedComponents = {
      val poolTag = batchSelection.info.poolTag
      val block = batchSelection.blocks.head
      logger.info(s"Now querying shares, pool states, and miner settings for block ${block.blockheight}" +
        s" and pool ${block.poolTag}")

      val shareBatcher = new BatchShareCollector(batchSelection, db, params)
      val fCollector = shareBatcher.batchCollect()
      val poolMinersResp = (db.run(Tables.PoolSharesTable.queryPoolMiners(poolTag, params.defaultPoolTag))).mapTo[Seq[SMinerSettings]]

      val futPlacements = for{
        minerSettings <- poolMinersResp
        collector     <- fCollector
      } yield createPlacements(minerSettings, collector, batchSelection)
      futPlacements.flatten.map(p => PrePlacement(p, batchSelection))
    }
    collectedComponents
  }

  def createPlacements(minerSettings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection): Future[Seq[PoolPlacement]] = {
    Future {
      val poolTag = batch.info.poolTag
      val poolInformation = batch.info

      val lastPlacements = Await.result(
        db.run(Tables.PoolPlacementsTable
          .filter(p => p.gEpoch === batch.blocks.head.gEpoch - ConcurrentBoxLoader.BLOCK_BATCH_SIZE && p.subpool === poolTag).result)
        , 500 seconds
      )

      val lastMembers = Await.result(
        db.run(Tables.SubPoolMembers
          .filter(_.subpool === batch.info.poolTag)
          .filter(_.g_epoch === batch.info.g_epoch)
          .result
        ), 500 seconds
      )
      val totalReward = Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum)
      val processor = PaymentRouter.routeProcessor(poolInformation, minerSettings, collector, batch, totalReward)

      if (lastPlacements.nonEmpty)
        processor.processNext(lastPlacements)
      else
        processor.processFirst(lastMembers)
    }
  }


  @deprecated
  def createFakeBoxMap(batch: BatchSelection): Map[Long, Seq[InputBox]] = {
    var blockBoxMap = Map.empty[Long, Seq[InputBox]]
    val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"

    // Make this a future
    val batchSum = Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum)
    val totalAmount = batch.info.currency match {
      case PoolInformation.CURR_ERG =>
        HoldingRoot.getMaxInputs(batchSum)
      case PoolInformation.CURR_TEST_TOKENS =>
        EmissionRoot.getMaxInputs(batchSum)
      case PoolInformation.CURR_NETA =>
        ExchangeEmissionsRoot.getMaxInputs(batchSum)
      case PoolInformation.CURR_ERG_COMET =>
        ProportionalEmissionsRoot.getMaxInputs(batchSum)
    }

    val fakeBox = {
      nodeConfig.getClient.execute{
        ctx =>
          ctx.newTxBuilder().outBoxBuilder()
            .value(totalAmount)
            .contract(nodeConfig.getNodeWallet.contract)
            .build()
            .convertToInputWith(dummyTxId, 0)
      }
    }

    blockBoxMap = Map(batch.blocks.head.blockheight -> Seq(fakeBox))
    blockBoxMap
  }
}