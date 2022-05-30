package tasks

import actors.BlockingDbWriter._
import actors.ExplorerRequestBus.ExplorerRequests.{BoxesById, FatalExplorerError, TimeoutError, TxById}
import actors.QuickDbReader.{PaidAtGEpoch, PlacementsByBlock, PoolBlocksByStatus, PoolMembersByGEpoch, QueryAllSubPools, QueryPoolInfo}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.explorer.Models.{Output, TransactionData}
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolBlock, PoolInformation, PoolMember, PoolPlacement, PoolState, Share}
import models.DatabaseModels.{Balance, BalanceChange, ChangeKeys, Payment}
import models.ResponseModels.writesChangeKeys
import org.ergoplatform.appkit.{ErgoClient, ErgoId}
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}
import play.db.NamedDatabase
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.lifted.ExtensionMethods

import java.time.{Instant, LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{higherKinds, postfixOps}
import scala.util.{Failure, Success, Try}

@Singleton
class DbCrossCheck @Inject()(system: ActorSystem, config: Configuration,
                                    @Named("quick-db-reader") query: ActorRef,
                                    @Named("blocking-db-writer") write: ActorRef,
                                    @Named("explorer-req-bus") expReq: ActorRef,
                                    protected val dbConfigProvider: DatabaseConfigProvider)
                                    extends HasDatabaseConfigProvider[PostgresProfile]{

  import dbConfig.profile.api._
  val logger: Logger = Logger("DatabaseCrossCheck")
  val taskConfig: TaskConfiguration = new TasksConfig(config).dbCrossCheckConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val ergoClient: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)
  var blockQueue: ArrayBuffer[Block] = ArrayBuffer.empty[Block]

  implicit val ec: ExecutionContext = contexts.taskContext
  if(taskConfig.enabled) {
    logger.info(db.source.toString)
    logger.info(dbConfig.profileName)
    logger.info(s"DatabaseCrossCheck will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>

        Try {
          checkProcessingBlocks
        }.recoverWith{
          case ex =>
            logger.error("There was a critical error while checking processing blocks!", ex)
            Failure(ex)
        }
        Try(checkDistributions).recoverWith{
          case ex =>
            logger.error("There was a critical error while checking distributions!", ex)
            Failure(ex)
        }
    })(contexts.taskContext)
  }

  def checkProcessingBlocks: Future[Unit] = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val queryBlocks = (query ? PoolBlocksByStatus(PoolBlock.PROCESSING)).mapTo[Seq[PoolBlock]]
    for{
      blocks <- queryBlocks
    } yield {
      blocks.foreach{
        block =>
          val queryPlacements = (query ? PlacementsByBlock(block.poolTag, block.blockheight)).mapTo[Seq[PoolPlacement]]
          queryPlacements.map {
            placements =>

              placements.groupBy(_.holding_id).keys.foreach {
                holdingId =>
                  verifyHoldingBoxes(block, holdingId)
              }
          }
      }
    }
  }
  def verifyHoldingBoxes(block: PoolBlock, holdingId: String): Unit = {
    implicit val timeout: Timeout = Timeout(60 seconds)

    val boxFromExp = (expReq ? BoxesById(ErgoId.create(holdingId)))
    boxFromExp.onComplete {
      case Success(value) =>
        value match {
          case outputOpt: Option[Output] =>
            outputOpt match {
              case Some(output) =>
                if(output.isOnMainChain && output.spendingTxId.isEmpty) {
                  logger.info(s"Found unspent holding box ${holdingId}, now updating to processed")
                  write ! UpdatePoolBlockStatus(PoolBlock.PROCESSED, block.blockheight)
                }else{
                  logger.warn(s"Holding box ${holdingId} was found, but was either on a forked chain or was already spent!")
                  logger.warn("Now deleting placements")
                  write ! DeletePlacementsAtBlock(block.poolTag, block.blockheight)
                  if(!output.isOnMainChain) {
                    logger.warn("Holding box was not on the main chain! Setting block status to confirmed!")
                    write ! UpdatePoolBlockStatus(PoolBlock.CONFIRMED, block.blockheight)
                  }else if(output.spendingTxId.isDefined){
                    logger.warn("Holding box was found to have an already spent transaction id! Setting block status to initiated!")
                    write ! UpdatePoolBlockStatus(PoolBlock.INITIATED, block.blockheight)
                  }
                }
              case None =>
                if (Instant.now().toEpochMilli - block.updated.toInstant(ZoneOffset.UTC).toEpochMilli > params.restartPlacements.toMillis) {
                  logger.warn(s"It has been ${params.restartPlacements.toString()} since block was updated," +
                    s" now restarting placements for pool ${block.poolTag}")
                  write ! DeletePlacementsAtBlock(block.poolTag, block.blockheight)
                  write ! UpdatePoolBlockStatus(PoolBlock.CONFIRMED, block.blockheight)
                } else {
                  logger.warn("ExplorerReqBus returned no Output, but restartPlacements time has not passed for this block!")
                }
            }
            logger.info(s"Completed updates for processing block ${block.blockheight}")
          case TimeoutError(ex) =>
            logger.error("Received a socket timeout from ExplorerRequestBus, refusing to modify pool states!")
            throw ex
          case FatalExplorerError(ex) =>
            logger.error("Received a fatal error from ExplorerRequestBus, refusing to modify pool states!")
            throw ex
        }
      case Failure(exception) =>
        logger.error(s"There was a critical error grabbing holding box ${holdingId} from the explorer!", exception)
    }
  }

  def checkDistributions: Unit = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val queryBlocks = (query ? PoolBlocksByStatus(PoolBlock.INITIATED)).mapTo[Seq[PoolBlock]]
    for(blocks <- queryBlocks){
      blocks.foreach{
        block =>
          val queryPoolStates = (query ? QueryAllSubPools(block.poolTag)).mapTo[Seq[PoolState]]
          validateDistStates(block, queryPoolStates)
        }
      }
    }

  def modifySuccessState(state: PoolState, txOpt: Option[TransactionData]): PoolState = {
        txOpt match {
          case Some(txData) =>
            // TODO: MIGHT HAVE TO CHANGE THIS CONSTANT LATER
            val holdingInput = txData.inputs(2)
            val nextStored = txData.outputs.find(o => o.address == holdingInput.address)
            val nextId = nextStored.map(s => s.id.toString).getOrElse("none")
            val nextVal = nextStored.map(s => s.value).getOrElse(0L)
            val nextState = state.makeConfirmed(txData.outputs.head.id.toString, nextId, nextVal)
            nextState
          case None =>
            if (Instant.now().toEpochMilli - state.updated.toInstant(ZoneOffset.UTC).toEpochMilli > params.restartDists.toMillis) {
              logger.warn(s"It has been ${params.restartDists.toString()} since block was updated, now setting" +
                s" subpool ${state.subpool_id} in pool ${state.subpool} to status failed")
              state.makeFailure
            } else {
              state
            }
        }
  }

  def validateDistStates(block: PoolBlock, queryPoolStates: Future[Seq[PoolState]]): Future[Unit] = {
    implicit val timeout: Timeout = Timeout(80 seconds)
    queryPoolStates.map {
      poolStates =>
        if(!poolStates.forall(s => s.status == PoolState.CONFIRMED)) {
          handleUnconfirmedStates(block, poolStates)
        }else{
          logger.info(s"All pools had status confirmed for pool ${poolStates.head.subpool}")
          logger.info("Now updating block and pool information")
          write ! UpdatePoolBlockStatus(PoolBlock.PAID, block.blockheight)
          val fPoolMembers = (query ? PoolMembersByGEpoch(block.poolTag, block.gEpoch)).mapTo[Seq[PoolMember]]
          val fPoolInfo = (query ? QueryPoolInfo(block.poolTag)).mapTo[PoolInformation]
          logger.info("Block status update complete")
          for{
            members <- fPoolMembers
            info <- fPoolInfo
          } yield {
            enterNewPaymentStats(block, info, members)
          }

        }
    }
  }

  def enterNewPaymentStats(block: PoolBlock, info: PoolInformation, members: Seq[PoolMember]): Try[Unit] = {
    // Build db changes
    // TODO: Fix payments for tokens
    val payments = members.filter(m => m.paid > 0).map{
      m =>
        Payment(block.poolTag, m.miner, info.currency, Helpers.convertFromWhole(info.currency, m.paid),
          m.tx, None, LocalDateTime.now(), block.blockheight, block.gEpoch)
    }

    val balances = members.map{
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
      write ! UpdatePoolInfo(block.poolTag, block.gEpoch, block.blockheight, members.length, members.map(_.stored).sum,
        info.total_paid + members.map(_.paid).sum)

      write ! DeletePlacementsAtBlock(block.poolTag, block.blockheight)
      logger.info("Finished updating info and deleting placements")
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while updating info and deleting placements!", ex)
        Failure(ex)
    }
    Try(logger.info("Pool data updates complete"))
  }

  def handleUnconfirmedStates(block: PoolBlock, poolStates: Seq[PoolState]): Unit = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val modifiedPoolStates = {
      val statesToCheck = poolStates.filter(s => s.status == PoolState.SUCCESS)
      val nextStates = Future.sequence(statesToCheck.map {
        state =>
          val verifyTx = (expReq ? TxById(ErgoId.create(state.tx)))
          logger.info(s"Modifying state for pool ${block.poolTag} with block ${block.blockheight}")
          val newState = verifyTx.map {
            case txOpt: Option[TransactionData] =>
              modifySuccessState(state.copy(g_epoch = block.gEpoch), txOpt)
            case TimeoutError(ex) =>
              logger.error("Received a socket timeout from ExplorerRequestBus, refusing to modify pool states!")
              throw ex
            case FatalExplorerError(ex) =>
              logger.error("Received a fatal error from ExplorerRequestBus, refusing to modify pool states!")
              throw ex
          }
          newState
      })
      nextStates
    }

    modifiedPoolStates.onComplete {
      case Success(newStates) =>
        logger.warn(s"Updating pool ${newStates.head.subpool} with ${newStates.count(s => s.status == PoolState.FAILURE)}" +
          s" failures and ${newStates.count(s => s.status == PoolState.CONFIRMED)} confirmations")
        write ! UpdateWithNewStates(newStates.toArray)

        logger.warn("Now deleting members for the failed subpools")
        val newFailedStates = newStates.filter(s => s.status == PoolState.FAILURE)
        if(newFailedStates.nonEmpty) {
          newStates.filter(s => s.status == PoolState.FAILURE).foreach {
            s =>
              write ! DeleteSubPoolMembers(block.poolTag, block.gEpoch, s.subpool_id)
          }
        }

        if(newStates.exists(s => s.status == PoolState.FAILURE) || poolStates.exists(s => s.status == PoolState.FAILURE)){
          logger.info("Now setting block back to processed state due to existence of failures")
          write ! UpdatePoolBlockStatus(PoolBlock.PROCESSED, block.blockheight)

        }
        logger.info("Pool state modifications complete")
      case Failure(exception) =>
        logger.error("There was a critical error while creating new pool states", exception)
    }
  }
}
