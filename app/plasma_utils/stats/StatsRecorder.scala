package plasma_utils.stats

import actors.BlockingDbWriter.{DeletePlacementsAtBlock, UpdatePoolInfo}
import actors.EmissionRequestHandler.CycleResponse
import akka.util.Timeout
import configs.ParamsConfig
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolBlock, PoolInformation, PoolMember, PoolState}
import io.getblok.subpooling_core.plasma.PoolBalanceState
import models.DatabaseModels.{Balance, BalanceChange, Payment, SPoolBlock}
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader.BatchSelection

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

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

  def writeProcessed(blocks: Seq[SPoolBlock], db: PostgresProfile#Backend#Database): Future[Int] = {
    val blockSort = blocks.sortBy(_.gEpoch)
    db.run(Tables.PoolBlocksTable
      .filter(_.poolTag === blockSort.head.poolTag)
      .filter(_.gEpoch >= blockSort.head.gEpoch)
      .filter(_.gEpoch <= blockSort.last.gEpoch)
      .map(b => b.status -> b.updated)
      .update(PoolBlock.PROCESSED -> LocalDateTime.now()))
  }

  def confirmTransform(poolState: PoolState, outputId: String, db: PostgresProfile#Backend#Database): Future[Int] = {
    db.run(
      Tables.PoolStatesTable
        .filter(_.subpool === poolState.subpool)
        .map(s => (s.box, s.status, s.updated))
        .update((outputId, PoolState.CONFIRMED, LocalDateTime.now()))
    )
  }

  def writePoolBalances(poolTag: String, balances: Seq[PoolBalanceState], db: PostgresProfile#Backend#Database)(implicit ec: ExecutionContext): Future[Unit] = {
    val currentPoolBals = db.run(Tables.PoolBalanceStateTable.filter(_.poolTag === poolTag).result)

    currentPoolBals.map{
      poolBals =>
        val balsToInsert = balances.filter(b => !poolBals.exists(pb => pb.miner == b.miner))
        val balsToUpdate = balances.filter(b => poolBals.exists(pb => pb.miner == b.miner))

        logger.info(s"Now inserting ${balsToInsert.length} new balance states")
        db.run(Tables.PoolBalanceStateTable ++= balsToInsert)

        logger.info(s"Now updating ${balsToUpdate.length} existing balance states")
        val balUpdates = {
          for(pb <- balsToUpdate) yield {
            db.run(
              Tables.PoolBalanceStateTable
                .filter(_.poolTag === poolTag)
                .filter(_.miner === pb.miner)
                .map(b => (b.gEpoch, b.tx, b.digest, b.command, b.step, b.balance, b.lastPaid, b.block, b.updated))
                .update((pb.gEpoch, pb.tx, pb.digest, pb.command, pb.step, pb.balance, pb.lastPaid, pb.block, pb.updated))
            )
          }
        }

        Future.sequence(balUpdates).onComplete{
          case Success(value) =>
            logger.info(s"Finished updating balance states, ${value.sum} states updated")
          case Failure(exception) =>
            logger.error("There was an error updating balance states!", exception)
        }
    }
  }

  def recordCycle(cycleResponse: CycleResponse, batch: BatchSelection, db: PostgresProfile#Backend#Database)(implicit ec: ExecutionContext): Future[Unit] = {
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

      balancesToUpdate.map{
        bU =>
          db.run(bU).recoverWith{
            case e: Exception =>
              logger.error("Fatal error while updating info!", e)
              Future(Failure(e))
          }
      }
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
      // Update pool info

      db.run(
        Tables.PoolInfoTable
          .filter(_.poolTag === info.poolTag)
          .map(i => (i.gEpoch, i.lastBlock, i.totalMembers, i.valueLocked, i.totalPaid))
          .update((block.gEpoch, block.blockheight, members.length, members.map(_.stored).sum,
            (info.total_paid + members.map(_.paid).sum))
          )
      ).recoverWith{
        case e: Exception =>
          logger.error("Fatal error while updating info!", e)
          Future(Failure(e))
      }


    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while updating info and deleting placements!", ex)
        Failure(ex)
    }

    Try{
      logger.info("Now deleting placements")
      db.run(Tables.PoolPlacementsTable.filter(_.subpool === info.poolTag).filter(_.block === block.blockheight).delete)
        .recoverWith{
          case e: Exception =>
            logger.error("Fatal error while deleting placements!", e)
            Future(Failure(e))
        }
      logger.info("Finished deleting placements")
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while deleting placements!", ex)
        Failure(ex)
    }

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
    Try(logger.info("Pool data updates complete"))
  }

}
