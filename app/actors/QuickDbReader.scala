package actors

import actors.DbConnectionManager.NewConnectionRequest
import actors.GroupRequestHandler.{DistributionResponse, ExecuteDistribution, PoolData, props}
import actors.QuickDbReader._
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import configs.{DbConfig, NodeConfig}
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, SimpleHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.builders.DistributionBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.LoadingSelector
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.{BlocksTable, InfoTable, MembersTable, PlacementTable, PoolBlocksTable, SettingsTable, SharesTable, StateTable}
import io.getblok.subpooling_core.persistence.models.Models.{Block, DbConn, PoolMember, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, ErgoId, NetworkType}
import persistence.shares.ShareHandler
import play.api.db.slick.DatabaseConfigProvider
import play.api.{Configuration, Logger}
import play.db.NamedDatabase
import slick.jdbc.PostgresProfile

import java.time.LocalDateTime
import javax.inject.{Inject, Named}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.language.postfixOps
import slick.jdbc.PostgresProfile.api._

import scala.util.{Failure, Try}

class QuickDbReader @Inject()(configuration: Configuration) extends Actor{
  private val log: Logger   = Logger("DBReader")
  private val dbConfig: DbConfig = new DbConfig(configuration)
  implicit val dbConn: DbConn = dbConfig.getNewConnection

  log.info("Initializing a new DBReader")

  override def receive: Receive = {
    case queryRequest: DatabaseQueryRequest =>
      Try {
        val stateTable = new StateTable(dbConn)
        val blocksTable = new BlocksTable(dbConn)
        val poolBlocksTable = new PoolBlocksTable(dbConn)
        val settingsTable = new SettingsTable(dbConn)
        val sharesTable = new SharesTable(dbConn)
        val infoTable = new InfoTable(dbConn)

        def membersTable(partition: String) = new MembersTable(dbConn, partition)

        def placementTable(partition: String) = new PlacementTable(dbConn, partition)
        // log.info("New query request was received!")
        queryRequest match {


          // Block queries
          case BlockByHeight(height: Long) =>
            sender() ! poolBlocksTable.queryByHeight(height)
          case PoolBlocksByStatus(status: String) =>
            sender() ! poolBlocksTable.queryByStatus(status)
          case QueryPending(limit) =>
            sender ! blocksTable.queryPendingBlocks(limit)
          case BlockById(id: Long) =>
            sender() ! poolBlocksTable.queryById(id)
          case QueryBlocks(poolTag) =>
            sender() ! poolBlocksTable.queryBlocks(poolTag)
          case BlockAtGEpoch(poolTag, gEpoch) =>
            sender() ! poolBlocksTable.queryBlockAtGEpoch(poolTag, gEpoch)
          // State queries
          case QueryAllSubPools(poolTag: String) =>
            sender() ! stateTable.queryAllSubPoolStates(poolTag)
          case QuerySubPool(poolTag: String, id: Long) =>
            sender() ! stateTable.querySubpoolState(poolTag, id)
          //        case QueryAllPoolStates =>
          //          sender() ! stateTable.queryAllPoolStates
          case QueryPoolStatesAtId(id) =>
            sender() ! stateTable.queryPoolStatesAtId(id)
          // Placement queries
          case PlacementsByBlock(poolTag: String, block: Long) =>
            sender() ! placementTable(poolTag).queryPlacementsForBlock(block)
          case PlacementsByGEpoch(poolTag: String, gEpoch: Long) =>
            sender() ! placementTable(poolTag).queryPlacementsByGEpoch(gEpoch)
          case PlacementsByMiner(poolTag: String, miner: String) =>
            sender() ! placementTable(poolTag).queryMinerPlacements(miner)
          case QueryMinerPending(poolTag: String, miner: String) =>
            sender() ! placementTable(poolTag).queryMinerPendingBalance(miner)
          case SubPoolPlacements(poolTag: String, id: Long, block: Option[Long]) =>
            if (block.isDefined)
              sender() ! placementTable(poolTag).querySubPoolPlacementsByBlock(id, block.get)
            else
              sender() ! placementTable(poolTag).querySubPoolPlacements(id)
          case PoolPlacements(poolTag: String) =>
            sender() ! placementTable(poolTag).queryPoolPlacements
          case QueryLastPlacement(poolTag: String) =>
            sender() ! placementTable(poolTag).queryLastPlacement
          // Members queries
          case SubPoolMembersByEpoch(poolTag, id, epoch) =>
            sender() ! membersTable(poolTag).querySubPoolMembersAtEpoch(id, epoch)
          case SubPoolMembersByBlock(poolTag, id, block) =>
            sender() ! membersTable(poolTag).querySubPoolMembersAtBlock(id, block)
          case SubPoolMembersByGEpoch(poolTag, id, gEpoch) =>
            sender() ! membersTable(poolTag).querySubPoolMembersAtGEpoch(id, gEpoch)
          case PoolMembersByGEpoch(poolTag, gEpoch) =>
            sender() ! membersTable(poolTag).queryPoolMembersAtGEpoch(gEpoch)
          case AllPoolMembers(poolTag) =>
            sender() ! membersTable(poolTag).queryAllPoolMembers
          case QueryMinerStored(poolTag, miner) =>
            sender() ! membersTable(poolTag).queryMinerCurrentStored(miner)
          case PaidAtGEpoch(poolTag, gEpoch) =>
            sender() ! membersTable(poolTag).queryPaidAtGEpoch(gEpoch)
          case MinersByAssignedPool(poolTag) =>
            sender() ! settingsTable.queryBySubpool(poolTag)
          case SettingsForMiner(miner) =>
            sender() ! settingsTable.queryByMiner(miner)
          /*case QueryWithShareHandler(paymentType, blockHeight, blockMiner) =>
            val collector = new ShareHandler(sharesTable, paymentType, blockMiner).queryToWindow(blockHeight)
            sender ! collector*/
          case QuerySharesBetween(start, end) =>
            sender ! sharesTable.queryBetween(start, end)
          case QuerySharesBefore(date) =>
            sender ! sharesTable.queryBefore(date)
          case QuickDbReader.QueryAllPoolInfo =>
            sender ! infoTable.queryAllPools
          case QueryPoolInfo(poolTag) =>
            sender ! infoTable.queryPool(poolTag)
          case QueryPoolsWithOfficial(official) =>
            sender ! infoTable.queryWithOfficial(official)
        }
      }.recoverWith{
        case ex: Exception =>
          log.error("There was a fatal error thrown by a DBReader!", ex)
          Failure(ex)
      }
  }
}

object QuickDbReader {
  def props: Props = Props[QuickDbReader]

  sealed trait QueryRequest

  class DatabaseQueryRequest
  // Quick Querying Protocol
  // Multiple values returned as Seq
  case class BlockByHeight(height: Long)    extends DatabaseQueryRequest
  case class QueryPending(limit: Int)                   extends DatabaseQueryRequest
  case class PoolBlocksByStatus(status: String) extends DatabaseQueryRequest
  case class BlockById(id: Long)            extends DatabaseQueryRequest
  case class QueryBlocks(poolTag: Option[String] = None)  extends DatabaseQueryRequest
  case class BlockAtGEpoch(poolTag: String, gEpoch: Long)  extends DatabaseQueryRequest

  case class QueryAllSubPools(poolTag: String)       extends DatabaseQueryRequest
  case class QuerySubPool(poolTag: String, id: Long) extends DatabaseQueryRequest
  // case class QueryAllPoolStates                     extends DatabaseQueryRequest
  case class QueryPoolStatesAtId(id: Long = 0)       extends DatabaseQueryRequest

  case class PlacementsByMiner(poolTag: String, miner: String) extends DatabaseQueryRequest
  case class PlacementsByBlock(poolTag: String, block: Long)   extends DatabaseQueryRequest
  case class PlacementsByGEpoch(poolTag: String, gEpoch: Long)   extends DatabaseQueryRequest
  case class QueryMinerPending(poolTag: String, miner: String) extends DatabaseQueryRequest
  case class SubPoolPlacements(poolTag: String, id: Long,
                               block: Option[Long])            extends DatabaseQueryRequest

  case class PoolPlacements(poolTag: String)                   extends DatabaseQueryRequest
  case class QueryLastPlacement(poolTag: String)               extends DatabaseQueryRequest

  case class SubPoolMembersByGEpoch(poolTag: String, id: Long, gEpoch: Long) extends DatabaseQueryRequest
  case class SubPoolMembersByEpoch(poolTag: String, id: Long, epoch: Long)   extends DatabaseQueryRequest
  case class SubPoolMembersByBlock(poolTag: String, id: Long, block: Long)   extends DatabaseQueryRequest
  case class PoolMembersByBlock(poolTag: String, block: Long)   extends DatabaseQueryRequest
  case class PoolMembersByGEpoch(poolTag: String, gEpoch: Long) extends DatabaseQueryRequest
  case class PaidAtGEpoch(poolTag: String, gEpoch: Long)        extends DatabaseQueryRequest
  case class AllPoolMembers(poolTag: String)                    extends DatabaseQueryRequest
  case class QueryMinerStored(poolTag: String, miner: String)   extends DatabaseQueryRequest

  case class MinersByAssignedPool(poolTag: String) extends DatabaseQueryRequest
  case class SettingsForMiner(miner: String)       extends DatabaseQueryRequest

  case object QueryAllPoolInfo                     extends DatabaseQueryRequest
  case class QueryPoolInfo(poolTag: String)        extends DatabaseQueryRequest

  case class QueryPoolsWithOfficial(official: Boolean) extends DatabaseQueryRequest

  @deprecated
  case class QueryWithShareHandler(paymentType: PaymentType, blockHeight: Long, blockMiner: String) extends DatabaseQueryRequest
  case class QuerySharesBetween(start: LocalDateTime, end: LocalDateTime) extends DatabaseQueryRequest
  case class QuerySharesBefore(date: LocalDateTime) extends DatabaseQueryRequest

}


