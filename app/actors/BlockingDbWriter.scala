package actors

import actors.BlockingDbWriter._
import actors.QuickDbReader._
import akka.actor.{Actor, Props}
import configs.DbConfig
import io.getblok.subpooling_core.node.NodeHandler.PartialBlockInfo
import io.getblok.subpooling_core.persistence._
import io.getblok.subpooling_core.persistence.models.Models._
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.language.postfixOps

class BlockingDbWriter @Inject()(configuration: Configuration) extends Actor{
  private val log: Logger   = Logger("DBWriter")
  private val dbConfig: DbConfig = new DbConfig(configuration)
  implicit val dbConn: DbConn = dbConfig.getNewConnection
  log.info("Initializing a new BlockingDbWriter")

  override def receive: Receive = {
    case updateRequest: DatabaseUpdateRequest =>
      val stateTable        = new StateTable(dbConn)
      val blocksTable       = new BlocksTable(dbConn)
      val poolBlocksTable   = new PoolBlocksTable(dbConn)
      val settingsTable = new SettingsTable(dbConn)
      val infoTable: InfoTable = new InfoTable(dbConn)
      def membersTable(partition: String)   = new MembersTable(dbConn, partition)
      def placementTable(partition: String) = new PlacementTable(dbConn, partition)
      // log.info("New update request was received!")
      updateRequest match {
        case UpdateBlockStatus(status, blockHeight) =>
          sender ! blocksTable.updateBlockStatus(status, blockHeight)
        case UpdatePoolBlockStatus(status, blockHeight) =>
          sender ! poolBlocksTable.updateBlockStatus(status, blockHeight)
        case UpdatePoolBlockConf(status, confirmation, blockHeight, gEpoch) =>
          sender ! poolBlocksTable.updateBlockStatusAndConfirmation(status, confirmation, blockHeight, gEpoch)
        case UpdateWithValidation(blockHeight, partialBlockInfo) =>
          sender ! poolBlocksTable.updateBlockValidation(blockHeight, partialBlockInfo)
        case PostBlock(blockHeight, poolTag) =>
          sender ! poolBlocksTable.insertWithBlock(blockHeight, poolTag)
        case UpdateBlockEffort(poolTag, effort, blockHeight) =>
          sender ! poolBlocksTable.updateBlockEffort(poolTag, effort, blockHeight)
        case InsertMembers(poolTag, memberSeq) =>
          sender ! membersTable(poolTag).insertMemberArray(memberSeq)
        case DeleteSubPoolMembers(poolTag, gEpoch, id) =>
          sender ! membersTable(poolTag).deleteByGEpochAndId(gEpoch, id)
        case InsertPlacements(poolTag, placementSeq) =>
          sender ! placementTable(poolTag).insertPlacementArray(placementSeq)
        case DeletePlacementsAtBlock(poolTag, blockHeight) =>
          sender ! placementTable(poolTag).deleteByBlock(blockHeight)
        case InsertPoolStates(states) =>
          sender ! stateTable.insertStateArray(states)
        case UpdatePoolGEpoch(poolTag, gEpoch) =>
          sender ! stateTable.updateGEpoch(poolTag, gEpoch)
        case UpdatePoolConfirm(poolTag, id, box, stored_id, stored_val) =>
          sender ! stateTable.updateConfirmed(poolTag, id, box, stored_id, stored_val)
        case UpdatePoolInit(poolTag, id, epoch, members, block) =>
          sender ! stateTable.updateInitiated(poolTag, id, epoch, members, block)
        case UpdatePoolSuccess(poolTag, id, tx, height) =>
          sender ! stateTable.updateSuccess(poolTag, id, tx, height)
        case UpdatePoolFail(poolTag, id, height) =>
          sender ! stateTable.updateFailure(poolTag, id, height)
        case UpdateWithNewStates(newStates) =>
          sender ! stateTable.updateManyStates(newStates)
        case UpdatePoolState(state) =>
          sender ! stateTable.updatePoolState(state)
        case UpdateMinerPool(address, poolTag) =>
          sender ! settingsTable.updateMinerPool(address, poolTag)
        case UpdateMinerMinPay(address, threshold) =>
          sender ! settingsTable.updateMinerPaymentThreshold(address, threshold)
        case InsertNewPoolInfo(info) =>
          sender ! infoTable.insertNewInfo(info)
        case UpdatePoolInfo(poolTag, gEpoch, lastBlock, totalMembers, valueLocked, totalPaid) =>
          sender ! infoTable.updatePoolInfo(poolTag, gEpoch, lastBlock, totalMembers, valueLocked, totalPaid)
        case UpdatePoolBlocksFound(poolTag, blocksFound) =>
          sender ! infoTable.updateBlocksFound(poolTag, blocksFound)
      }


  }
}

object BlockingDbWriter {
  def props: Props = Props[BlockingDbWriter]
  sealed trait DatabaseUpdateRequest
  // Blocks
  case class UpdateBlockStatus(status: String, blockHeight: Long)       extends DatabaseUpdateRequest
  case class UpdatePoolBlockStatus(status: String, blockHeight: Long)       extends DatabaseUpdateRequest
  case class UpdatePoolBlockConf(status: String,
                                 confirmation: Double, blockHeight: Long, gEpoch: Option[Long])   extends DatabaseUpdateRequest
  case class UpdateBlockEffort(poolTag: String, effort: Double, blockHeight: Long) extends DatabaseUpdateRequest
  case class PostBlock(blockHeight: Long, poolTag: String)              extends  DatabaseUpdateRequest
  case class UpdateWithValidation(blockHeight: Long,
                                  partialBlockInfo: PartialBlockInfo)   extends DatabaseUpdateRequest
  // Members
  case class InsertMembers(poolTag: String,
                           memberSeq: Array[PoolMember])                extends DatabaseUpdateRequest

  case class DeleteSubPoolMembers(poolTag: String, gEpoch: Long,
                                  id: Long)                             extends DatabaseUpdateRequest
  // Placements
  case class InsertPlacements(poolTag: String,
                              placementSeq: Array[PoolPlacement])       extends DatabaseUpdateRequest

  case class DeletePlacementsAtBlock(poolTag: String,
                                     blockHeight: Long)                 extends DatabaseUpdateRequest
  // Pool States
  case class InsertPoolStates(states: Array[PoolState])                 extends DatabaseUpdateRequest
  case class UpdatePoolGEpoch(poolTag: String, gEpoch: Long)            extends DatabaseUpdateRequest

  case class UpdatePoolConfirm(poolTag: String, id: Long,
                               box: String, stored_id: String,
                               stored_val: Long)                        extends DatabaseUpdateRequest

  case class UpdatePoolInit(poolTag: String, id: Long, epoch: Long,
                            members: Int, block: Long)                  extends DatabaseUpdateRequest

  case class UpdatePoolFail(poolTag: String, id: Long, height: Long)    extends DatabaseUpdateRequest

  case class UpdatePoolSuccess(poolTag: String, id: Long, tx: String,
                               height: Long)                            extends DatabaseUpdateRequest

  case class UpdateWithNewStates(newStates: Array[PoolState])           extends DatabaseUpdateRequest

  case class UpdatePoolState(state: PoolState)                          extends DatabaseUpdateRequest

  // Settings
  case class UpdateMinerPool(address: String, poolTag: String)          extends DatabaseUpdateRequest
  case class UpdateMinerMinPay(address: String, threshold: Double)      extends DatabaseUpdateRequest

  case class InsertNewPoolInfo(info: PoolInformation)                   extends DatabaseUpdateRequest

  case class UpdatePoolInfo(poolTag: String, gEpoch: Long, lastBlock: Long, totalMembers: Long, valueLocked: Long,
                            totalPaid: Long)                            extends DatabaseUpdateRequest
  case class UpdatePoolBlocksFound(poolTag: String, blocksFound: Long) extends DatabaseUpdateRequest
  case class UpdateResponse(rowsUpdated: Long)
}




