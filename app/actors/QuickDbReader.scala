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
import io.getblok.subpooling_core.payments.ShareHandler
import io.getblok.subpooling_core.persistence.{BlocksTable, InfoTable, MembersTable, PlacementTable, SettingsTable, SharesTable, StateTable}
import io.getblok.subpooling_core.persistence.models.Models.{Block, DbConn, PoolMember, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, ErgoId, NetworkType}
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Named}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.language.postfixOps

class QuickDbReader @Inject()(configuration: Configuration) extends Actor{
  private val log: Logger   = Logger("QuickDbReader")
  private val dbConfig: DbConfig = new DbConfig(configuration)
  implicit val dbConn: DbConn = dbConfig.getNewConnection
  log.info("Initializing a new QuickDbReader")

  override def receive: Receive = {
    case queryRequest: DatabaseQueryRequest =>
      val stateTable    = new StateTable(dbConn)
      val blocksTable   = new BlocksTable(dbConn)
      val settingsTable = new SettingsTable(dbConn)
      val sharesTable = new SharesTable(dbConn)
      val infoTable   = new InfoTable(dbConn)
      def membersTable(partition: String)   = new MembersTable(dbConn, partition)
      def placementTable(partition: String) = new PlacementTable(dbConn, partition)
      log.info("New query request was received!")
      queryRequest match {


        // Block queries
        case BlockByHeight(height: Long) =>
          sender() ! blocksTable.queryByHeight(height)
        case BlocksByStatus(status: String) =>
          sender() ! blocksTable.queryByStatus(status)
        case BlockById(id: Long) =>
          sender() ! blocksTable.queryById(id)
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
        case PlacementsByMiner(poolTag: String, miner: String) =>
          sender() ! placementTable(poolTag).queryMinerPlacements(miner)
        case QueryMinerPending(poolTag: String, miner: String) =>
          sender() ! placementTable(poolTag).queryMinerPendingBalance(miner)
        case SubPoolPlacements(poolTag: String, id: Long, block: Option[Long]) =>
          if(block.isDefined)
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
        case MinersByAssignedPool(poolTag) =>
          sender() ! settingsTable.queryBySubpool(poolTag)
        case SettingsForMiner(miner) =>
          sender() ! settingsTable.queryByMiner(miner)
        case QueryWithShareHandler(paymentType, blockHeight) =>
          val collector = new ShareHandler(sharesTable, paymentType).queryToWindow(blockHeight)
          sender ! collector
        case QuickDbReader.QueryAllPoolInfo =>
          sender ! infoTable.queryAllPools
        case QueryPoolInfo(poolTag) =>
          sender ! infoTable.queryPool(poolTag)
        case QueryPoolsWithOfficial(official) =>
          sender ! infoTable.queryWithOfficial(official)
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
  case class BlocksByStatus(status: String) extends DatabaseQueryRequest
  case class BlockById(id: Long)            extends DatabaseQueryRequest

  case class QueryAllSubPools(poolTag: String)       extends DatabaseQueryRequest
  case class QuerySubPool(poolTag: String, id: Long) extends DatabaseQueryRequest
  // case class QueryAllPoolStates                     extends DatabaseQueryRequest
  case class QueryPoolStatesAtId(id: Long = 0)       extends DatabaseQueryRequest

  case class PlacementsByMiner(poolTag: String, miner: String) extends DatabaseQueryRequest
  case class PlacementsByBlock(poolTag: String, block: Long)   extends DatabaseQueryRequest
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
  case class AllPoolMembers(poolTag: String)                    extends DatabaseQueryRequest
  case class QueryMinerStored(poolTag: String, miner: String)   extends DatabaseQueryRequest

  case class MinersByAssignedPool(poolTag: String) extends DatabaseQueryRequest
  case class SettingsForMiner(miner: String)       extends DatabaseQueryRequest

  case object QueryAllPoolInfo                     extends DatabaseQueryRequest
  case class QueryPoolInfo(poolTag: String)        extends DatabaseQueryRequest

  case class QueryPoolsWithOfficial(official: Boolean) extends DatabaseQueryRequest


  case class QueryWithShareHandler(paymentType: PaymentType, blockHeight: Long) extends DatabaseQueryRequest

}


