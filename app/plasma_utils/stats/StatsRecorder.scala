package plasma_utils.stats

import actors.BlockingDbWriter.{DeletePlacementsAtBlock, UpdatePoolInfo}
import akka.util.Timeout
import configs.ParamsConfig
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.Models.{PoolBlock, PoolInformation, PoolMember, PoolState}
import models.DatabaseModels.{Balance, BalanceChange, Payment, SPoolBlock}
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import slick.jdbc.PostgresProfile

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Try}

object StatsRecorder {
  private val logger: Logger = LoggerFactory.getLogger("StatsRecorder")
  import slick.jdbc.PostgresProfile.api._
  private val timeout: Timeout = Timeout(1000 seconds)

  def writePaidBlocks(blocks: Seq[SPoolBlock], db: PostgresProfile#Backend#Database): Future[Int] = {
    val blockSort = blocks.sortBy(_.gEpoch)
    db.run(Tables.PoolBlocksTable
      .filter(_.poolTag === blockSort.head.poolTag)
      .filter(_.gEpoch >= blockSort.head.gEpoch)
      .filter(_.gEpoch <= blockSort.last.gEpoch)
      .map(b => b.status -> b.updated)
      .update(PoolBlock.PAID -> LocalDateTime.now()))
  }

  def confirmTransform(poolState: PoolState, outputId: String, db: PostgresProfile#Backend#Database): Future[Int] = {
    db.run(
      Tables.PoolStatesTable
        .filter(_.subpool === poolState.subpool)
        .map(s => (s.box, s.status, s.updated))
        .update((outputId, PoolState.CONFIRMED, LocalDateTime.now()))
    )
  }

  def enterNewPaymentStats(block: SPoolBlock, info: PoolInformation, members: Seq[PoolMember], params: ParamsConfig,
                           settings: Map[String, Option[String]], db: PostgresProfile#Backend#Database)(implicit ec: ExecutionContext): Try[Unit] = {
    // Build db changes
    // TODO: Fix payments for tokens
    val payments = members.filter(m => m.paid > 0).map{
      m =>
        Payment(block.poolTag, m.miner, info.currency, Helpers.convertFromWhole(info.currency, m.paid),
          m.tx, None, LocalDateTime.now(), block.blockheight, block.gEpoch)
    }

    val balances = members.filter(m => m.subpool == settings.get(m.miner).flatten.getOrElse(params.defaultPoolTag)).map{
      m =>
        m.miner -> Helpers.convertFromWhole(info.currency, m.stored)
    }

    val balanceChanges = members.filter(_.change > 0).map{
      m =>
        BalanceChange(block.poolTag, m.miner, info.currency, Helpers.convertFromWhole(info.currency, m.change),
          m.tx, None, LocalDateTime.now(), block.blockheight, block.gEpoch)
    }

    val balancesToUpdate = balances.map{
      b =>
        Tables.Balances.insertOrUpdate(Balance(AppParameters.mcPoolId, b._1, b._2, LocalDateTime.now(), LocalDateTime.now()))
    }
    // Execute inserts and updates
    Try {
      logger.info("Initiating database writes")
      val paymentInserts = Tables.Payments ++= payments
      val changeInserts = Tables.BalanceChanges ++= balanceChanges

      balancesToUpdate.map(db.run)
      logger.info("Sending writes...")
      val payR = db.run(paymentInserts)
      val changeR = db.run(changeInserts)

      payR.recoverWith {
        case ex: Exception =>
          logger.error("There was an exception inserting payments!", ex)
          Future(Failure(ex))
      }
      changeR.recoverWith {
        case ex: Exception =>
          logger.error("There was an exception inserting balance changes!", ex)
          Future(Failure(ex))
      }
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while updating payment info!", ex)
        Failure(ex)
    }
    logger.info("Payment insertions complete")
    Try {
      val shareDeletes = Tables.PoolSharesTable.filter(_.created < LocalDateTime.now().minusWeeks(params.keepSharesWindowInWeeks))
      val statsDeletes = Tables.MinerStats.filter(_.created < LocalDateTime.now().minusWeeks(params.keepMinerStatsWindowInWeeks))
      val shareArcInserts = Tables.SharesArchiveTable forceInsertQuery shareDeletes
      val statsArcInserts = Tables.MinerStatsArchiveTable forceInsertQuery statsDeletes
      val shareInserts = db.run(shareArcInserts)
      val statsInserts = db.run(statsArcInserts)
      for {
        shareI <- shareInserts
        statsI <- statsInserts
      } yield {
        db.run(shareDeletes.delete)
        db.run(statsDeletes.delete)
      }
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while pruning shares and minerstats tables!", ex)
        Failure(ex)
    }
    logger.info("Table pruning complete")
    Try {
      // Update pool info

      db.run(
        Tables.PoolInfoTable
          .filter(_.poolTag === info.poolTag)
          .map(i => (i.gEpoch, i.lastBlock, i.totalMembers, i.valueLocked, i.totalPaid))
          .update((block.gEpoch, block.blockheight, members.length, members.map(_.stored).sum,
            (info.total_paid + members.map(_.paid).sum))
          )
      )

      db.run(Tables.PoolPlacementsTable.filter(_.subpool === info.poolTag).filter(_.block === block.blockheight).delete)
      logger.info("Finished updating info and deleting placements")
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while updating info and deleting placements!", ex)
        Failure(ex)
    }
    Try(logger.info("Pool data updates complete"))
  }

}
