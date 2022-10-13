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
  val logger: Logger = LoggerFactory.getLogger("EmissionHandler")
  import slick.jdbc.PostgresProfile.api._

  def startEmissions(): Unit = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext

    logger.info("Now querying pre-processed blocks for emissions")
    val blockResp = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PRE_PROCESSED).sortBy(_.created).result)
    val infoResp = db.run(Tables.PoolInfoTable.result)

    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds)
    val infos = Await.result(infoResp, 1000 seconds)
    logger.info("Number of normal blocks")
    val plasmaBlocks = PaymentRouter.routePlasmaBlocks(blocks, infos, routePlasma = true)
    logger.info(s"Num plasma blocks: ${plasmaBlocks.size}")
    if(plasmaBlocks.nonEmpty) {
      logger.info("Found plasma blocks to attempt emissions")
      val selectedBlocks = boxLoader.selectBlocks(plasmaBlocks, strictBatch = true, isPlasma = true)
      logger.info("Blocks selected, now collecting inputs")
      val inputBoxes = collectInputs(selectedBlocks)
      logger.info("Inputs collected, now executing cycle")
      val cycleResponse = executeCycle(selectedBlocks, inputBoxes)

      cycleResponse.onComplete {
        case Success(cycleResp) =>
          logger.info(s"Successfully executed cycle for pool ${selectedBlocks.info.poolTag}")
          logger.info("Now writing cycle response")
          writeCycle(cycleResp, selectedBlocks)
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
    StatsRecorder.recordCycle(cycleResponse, batch, db)
  }

  def executeCycle(batchSelection: BatchSelection, boxes: Seq[InputBox]): Future[CycleResponse] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = {
      logger.info(s"Now executing cycle for pool ${batchSelection.info.poolTag}")
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
        logger.info(s"Now executing emission cycle for pool ${placements.head.subpool}")
        val cycleResponse = (emHandler ? CycleEmissions(cycle, withMinpay, boxes)).mapTo[CycleResponse]
        cycleResponse
      }

      cycleComponents.flatten
    }
    collectedComponents
  }


  // TODO: Currently vertically scaled, consider horizontal scaling with Seq[BatchSelections]
  def collectInputs(batchSelection: BatchSelection): Seq[InputBox] = {
    val blockSum = Helpers.ergToNanoErg(batchSelection.blocks.map(_.reward).sum) + (Helpers.OneErg * 1)
    boxLoader.collectFromLoaded(blockSum).toSeq
  }

}
