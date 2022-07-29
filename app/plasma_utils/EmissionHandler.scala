package plasma_utils

import actors.EmissionRequestHandler.{ConstructCycle, CycleEmissions, CycleResponse}
import actors.StateRequestHandler._
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.cycles.models.Cycle
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolBlock, PoolPlacement, PoolState}
import io.getblok.subpooling_core.plasma.StateConversions.balanceConversion
import io.getblok.subpooling_core.plasma.{BalanceState, SingleBalance, StateBalance}
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.ergoplatform.appkit.InputBox
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import plasma_utils.payments.PaymentRouter
import plasma_utils.stats.StatsRecorder
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader
import utils.ConcurrentBoxLoader.{BatchSelection, PLASMA_BATCH_SIZE}

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class EmissionHandler(expReq: ActorRef, emHandler: ActorRef,
                      contexts: Contexts, params: ParamsConfig, taskConf: TaskConfiguration,
                      boxLoader: ConcurrentBoxLoader, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("PaymentDistributor")
  import slick.jdbc.PostgresProfile.api._

  def startEmissions(): Unit = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    logger.info("Now querying pre-processed blocks for emissions")
    val blockResp = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PRE_PROCESSED).sortBy(_.created).result)
    val infoResp = db.run(Tables.PoolInfoTable.result)
    logger.info(s"Querying blocks with processed status")
    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds)
    val infos = Await.result(infoResp, 1000 seconds)

    val plasmaBlocks = PaymentRouter.routePlasmaBlocks(blocks, infos, routePlasma = true)
    if(plasmaBlocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(plasmaBlocks, strictBatch = true, isPlasma = true)
      val inputBoxes = collectInputs(selectedBlocks)
      val cycleResponse = executeCycle(selectedBlocks, inputBoxes)

      cycleResponse.onComplete {
        case Success(cycleResp) =>
          logger.info(s"Successfully executed cycle for pool ${selectedBlocks.info.poolTag}")
          logger.info("Now writing cycle response")

        case Failure(exception) =>
          logger.error("An error occurred during cycle execution!", exception)
      }
    }else{
      logger.info("No processing blocks found, now exiting distribution execution")
    }
  }

  def writeCycle(cycleResponse: CycleResponse, batch: BatchSelection): Unit = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val blockUpdate = (db.run(Tables.PoolBlocksTable
      .filter(_.poolTag === batch.info.poolTag)
      .filter(_.gEpoch >= batch.blocks.head.gEpoch)
      .filter(_.gEpoch <= batch.blocks.last.gEpoch)
      .map(b => b.status -> b.updated)
      .update(PoolBlock.PROCESSING -> LocalDateTime.now())))

    val placeDeletes = {
      db.run(Tables.PoolPlacementsTable
        .filter(p => p.subpool === batch.blocks.head.poolTag)
        .filter(p => p.block === batch.blocks.minBy(b => b.gEpoch).blockheight)
        .delete)
    }
    val placeUpdates = placeDeletes.map {
      i =>
        logger.info(s"A total of ${i} rows were deleted from last placements")
        logger.info("Now inserting new placement rows!")
        db.run(Tables.PoolPlacementsTable ++= cycleResponse.nextPlacements)
    }.flatten

    for {
      blockRowsUpdated <- blockUpdate
      placeRowsInserted <- placeUpdates
    } yield {
      if (blockRowsUpdated > 0)
        logger.info(s"${blockRowsUpdated} rows were updated for ${batch.blocks.length} blocks")
      else
        logger.error(s"No rows were updated for ${batch.blocks.length} blocks!")
      if (placeRowsInserted.getOrElse(0) > 0)
        logger.info(s"${placeRowsInserted} rows were inserted for placements for pool ${cycleResponse.nextPlacements.head.subpool}")
      else
        logger.error(s"No placements were inserted for pool ${cycleResponse.nextPlacements.head.subpool}")
    }
  }

  def executeCycle(batchSelection: BatchSelection, boxes: Seq[InputBox]): Future[CycleResponse] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = {
      val poolTag = batchSelection.info.poolTag
      val block = batchSelection.blocks.head


      val fPlacements = (db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag && p.block === block.blockheight).result)).mapTo[Seq[PoolPlacement]]
      val poolMinersResp = (db.run(Tables.PoolSharesTable.queryPoolMiners(poolTag, params.defaultPoolTag))).mapTo[Seq[SMinerSettings]]

      val cycleComponents = for{
        placements <- fPlacements
        settings <- poolMinersResp
      } yield {
        val withMinpay = placements.map{
          p =>
            p.copy(minpay = settings.find(s => s.address == p.miner).map(s => Helpers.ergToNanoErg(s.paymentthreshold)).getOrElse(p.minpay))
        }
        val totalReward = Helpers.ergToNanoErg(batchSelection.blocks.map(_.reward).sum)
        val cycle = Await.result((emHandler ? ConstructCycle(batchSelection, totalReward)).mapTo[Cycle], 500 seconds)

        val cycleResponse = (emHandler ? CycleEmissions(cycle, withMinpay, boxes)).mapTo[CycleResponse]
        cycleResponse
      }

      cycleComponents.flatten
    }
    collectedComponents
  }


  // TODO: Currently vertically scaled, consider horizontal scaling with Seq[BatchSelections]
  def collectInputs(batchSelection: BatchSelection): Seq[InputBox] = {
    val blockSum = Helpers.ergToNanoErg(batchSelection.blocks.map(_.reward).sum) + (Helpers.OneErg * 2)
    boxLoader.collectFromLoaded(blockSum).toSeq
  }

}
