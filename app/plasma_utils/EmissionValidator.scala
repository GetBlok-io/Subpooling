package plasma_utils

import actors.ExplorerRequestBus.ExplorerRequests.{BoxesById, FatalExplorerError, TimeoutError, TxById}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.explorer.Models.{Output, TransactionData}
import io.getblok.subpooling_core.persistence.models.PersistenceModels._
import models.DatabaseModels.SPoolBlock
import org.ergoplatform.appkit.ErgoId
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import plasma_utils.payments.PaymentRouter
import plasma_utils.stats.StatsRecorder
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Try

class EmissionValidator(expReq: ActorRef, contexts: Contexts, params: ParamsConfig,
                        db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("TransformValidator")
  import slick.jdbc.PostgresProfile.api._
  implicit val timeout: Timeout = Timeout(1000 seconds)
  implicit val taskContext: ExecutionContext = contexts.taskContext

  def checkProcessing(): Future[Unit] = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val queryBlocks = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PROCESSING).sortBy(_.created).result)
    val qPools = db.run(Tables.PoolInfoTable.result)
    for {
      blocks <- queryBlocks
      pools <- qPools
    }
    yield {
      val plasmaBlocks = PaymentRouter.routePlasmaBlocks(blocks, pools, routePlasma = true)
      val pooledBlocks = plasmaBlocks.groupBy(_.poolTag)
      for (poolBlock <- pooledBlocks) {
        val poolToUse = pools.find(p => p.poolTag == poolBlock._1).get
        val blocksToUse = {
          if(poolToUse.payment_type == PoolInformation.PAY_SOLO){
            poolBlock._2.take(ConcurrentBoxLoader.PLASMA_BATCH_SIZE).sortBy(_.gEpoch)
          }else{
            poolBlock._2.take(ConcurrentBoxLoader.PLASMA_BATCH_SIZE).sortBy(_.gEpoch)
          }
        }
        val placements = db.run(
          Tables.PoolPlacementsTable
            .filter(_.subpool === blocksToUse.head.poolTag)
            .filter(_.block === blocksToUse.head.blockheight)
            .result
        )
        placements.map(p => validateHolding(blocksToUse, p))
      }
    }
  }

  def validateHolding(blocks: Seq[SPoolBlock], placements: Seq[PoolPlacement]): Unit = {
    require(placements.forall(p => p.holding_id == placements.head.holding_id), "Not all placements had the same holding id!")
    val boxResp = (expReq ? BoxesById(ErgoId.create(placements.head.holding_id)))
    for(box <- boxResp){
      box match {
        case opt: Option[Output] =>
          checkHolding(opt, blocks)
        case TimeoutError(ex) =>
          logger.error(s"There was a timeout error while validating transforms for pool ${blocks.head.poolTag}!", ex)
          logger.warn("Not modifying pool states due to timeout error")
        case FatalExplorerError(ex) =>
          logger.error(s"There was a fatal explorer error while validating transforms for pool ${blocks.head.poolTag}!", ex)
          logger.warn("Not modifying pool states due to fatal explorer error")
      }
    }
  }

  def checkHolding(opt: Option[Output], blocks: Seq[SPoolBlock]): Unit = {
    opt match {
      case Some(output) =>
        if(output.isOnMainChain && output.spendingTxId.isEmpty) {
          logger.info("Holding box found on-chain!")
          StatsRecorder.writeProcessed(blocks, db)
        }else{
          if(!output.isOnMainChain){
            logger.warn("Did not find holding box on main chain! Maybe its on a forked one?")
          }
          if(output.spendingTxId.isDefined){
            logger.warn("Found a spending transaction on the holding box! Has it already been used?")
          }
          logger.warn("Not modifying blocks for incorrect holding box")
        }
      case None =>
        logger.warn(s"No holding box found for pool ${blocks.head.poolTag}!")

        if (Instant.now().toEpochMilli - blocks.head.updated.toInstant(ZoneOffset.UTC).toEpochMilli > params.restartPlacements.toMillis) {

          logger.warn(s"It has been ${params.restartPlacements.toString()} since block was updated," +
            s" now restarting placements for pool ${blocks.head.poolTag}")

          db.run(Tables.PoolBlocksTable
            .filter(b => b.poolTag === blocks.head.poolTag)
            .filter(b => b.gEpoch >= blocks.head.gEpoch && b.gEpoch <= blocks.last.gEpoch)
            .map(b => b.status -> b.updated)
            .update(PoolBlock.PRE_PROCESSED, LocalDateTime.now()))
        } else {
          logger.warn("RestartPlacements time has not passed yet, not modifying blocks")
        }
    }
  }



}
