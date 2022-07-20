package plasma_utils

import actors.ExplorerRequestBus.ExplorerRequests.{FatalExplorerError, TimeoutError, TxById}
import actors.QuickDbReader.QueryAllSubPools
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.{Contexts, NodeConfig, ParamsConfig}
import io.getblok.subpooling_core.explorer.Models.{Output, TransactionData}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.persistence.models.Models._
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.ergoplatform.appkit.{ErgoId, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import persistence.shares.ShareCollector
import plasma_utils.payments.PaymentRouter
import plasma_utils.shares.BatchShareCollector
import plasma_utils.stats.StatsRecorder
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader
import utils.ConcurrentBoxLoader.BatchSelection

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class TransformValidator(expReq: ActorRef, contexts: Contexts, params: ParamsConfig,
                         db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("TransformValidator")
  import slick.jdbc.PostgresProfile.api._
  implicit val timeout: Timeout = Timeout(1000 seconds)
  implicit val taskContext: ExecutionContext = contexts.taskContext

  def checkInitiated(): Future[Unit] = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val queryBlocks = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.INITIATED).sortBy(_.created).result)
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
            poolBlock._2.take(ConcurrentBoxLoader.BLOCK_BATCH_SIZE).sortBy(_.gEpoch)
          }else{
            poolBlock._2.take(ConcurrentBoxLoader.BLOCK_BATCH_SIZE).sortBy(_.gEpoch)
          }
        }
        val poolState = db.run(Tables.PoolStatesTable.filter(_.subpool === poolBlock._1).result.head)
        poolState.map(s => validateTransform(blocks, s))
      }
    }
  }

  def validateTransform(blocks: Seq[SPoolBlock], poolState: PoolState): Unit = {
    val txResp = (expReq ? TxById(ErgoId.create(poolState.tx)))
    for(tx <- txResp){
      tx match {
        case opt: Option[TransactionData] =>
          checkTransform(opt, blocks, poolState)
        case TimeoutError(ex) =>
          logger.error(s"There was a timeout error while validating transforms for pool ${blocks.head.poolTag}!", ex)
          logger.warn("Not modifying pool states due to timeout error")
        case FatalExplorerError(ex) =>
          logger.error(s"There was a fatal explorer error while validating transforms for pool ${blocks.head.poolTag}!", ex)
          logger.warn("Not modifying pool states due to fatal explorer error")
      }
    }
  }

  def checkTransform(opt: Option[TransactionData], blocks: Seq[SPoolBlock], poolState: PoolState): Unit = {
    opt match {
      case Some(data) =>
        logger.info("Transaction found on-chain!")
        val nextOutput = data.outputs.head
        StatsRecorder.writePaidBlocks(blocks, db)
        StatsRecorder.confirmTransform(poolState, nextOutput.id.toString, db)
        writePaymentStats(poolState, blocks)
      case None =>
        logger.warn(s"No transaction found for pool ${blocks.head.poolTag}!")
        logger.warn("Not modifying transforms")
    }
  }

  def writePaymentStats(poolState: PoolState, blocks: Seq[SPoolBlock]): Future[Try[Unit]] = {
    val fMembers = db.run(
      Tables.SubPoolMembers
        .filter(_.subpool === poolState.subpool)
        .filter(_.g_epoch === blocks.head.gEpoch)
        .result
    )

    val fInfo = db.run(Tables.PoolInfoTable.filter(_.poolTag === blocks.head.poolTag).result.head)
    val fSettings = db.run(Tables.PoolSharesTable.queryMinerPools)

    for{
      members <- fMembers
      info <- fInfo
      settings <- fSettings
    } yield {
      StatsRecorder.enterNewPaymentStats(blocks.minBy(_.gEpoch), info, members, params, settings.toMap, db)
    }
  }


}
