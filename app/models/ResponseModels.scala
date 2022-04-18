package models

import actors.GroupRequestHandler.DistributionResponse
import io.getblok.subpooling_core.global.Helpers
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import io.getblok.subpooling_core.persistence.models.Models._
import io.getblok.subpooling_core.registers.PoolFees
object ResponseModels {
  case class PoolGenerated(poolName: String, poolTag: String, numSubpools: Int,
                           txId: String, creator: String, height: Long, timestamp: String)
  implicit val poolGenWrites: Writes[PoolGenerated] = new Writes[PoolGenerated] {
    def writes(gen: PoolGenerated): JsObject = Json.obj(
      "poolName" -> gen.poolName,
      "poolTag"     -> gen.poolTag,
      "numSubpools" -> gen.numSubpools,
      "creator" -> gen.creator,
      "txId" -> gen.txId,
      "height" -> gen.height,
      "timestamp" -> gen.timestamp
    )
  }

  implicit val poolStateWrites: Writes[PoolState] = new Writes[PoolState] {
    override def writes(o: PoolState): JsValue = {
      Json.obj( fields =
        "id" -> o.subpool_id,
        "name" -> o.title,
        "boxId" -> o.box,
        "txId" -> o.tx,
        "globalEpoch" -> o.g_epoch,
        "epoch" -> o.epoch,
        "genesisHeight" -> o.g_height,
        "epochHeight" -> o.height,
        "status" -> o.status,
        "numMembers" -> o.members,
        "currentBlock" -> o.block,
        "creator" -> o.creator,
        "storageBoxId" -> o.stored_id,
        "storageBoxValue" -> o.stored_val,
        "lastUpdated" -> o.updated.toString,
        "created" -> o.created.toString
      )
    }
  }

  implicit val blockWrites: Writes[Block] = new Writes[Block] {
    override def writes(o: Block): JsValue = {
      Json.obj( fields =
        "id" -> o.id,
        "blockHeight" -> o.blockheight,
        "status" -> o.status,
        "miner" -> o.miner,
        "confirmation" -> o.confirmationprogress,
        "reward" -> o.reward,
        "created" -> o.created.toString
      )
    }
  }

  implicit val poolPlaceWrites: Writes[PoolPlacement] = new Writes[PoolPlacement] {
    override def writes(o: PoolPlacement): JsValue = {
      Json.obj( fields =
        "pool" -> o.subpool,
        "id" -> o.subpool_id,
        "epoch" -> o.epoch,
        "globalEpoch" -> o.g_epoch,
        "block" -> o.block,
        "holdingBoxId" -> o.holding_id,
        "holdingBoxValue" -> o.holding_val,
        "miner" -> o.miner,
        "shareScore" -> o.score,
        "minPay" -> Helpers.nanoErgToErg(o.minpay),
        "epochsMined" -> o.epochs_mined,
        "amountAdded" -> Helpers.nanoErgToErg(o.amount),

      )
    }
  }

  implicit val poolMemberWrites: Writes[PoolMember] = new Writes[PoolMember] {
    override def writes(o: PoolMember): JsValue = {
      Json.obj( fields =
        "pool" -> o.subpool,
        "id" -> o.subpool_id,
        "globalEpoch" -> o.g_epoch,
        "epoch" -> o.epoch,
        "epochHeight" -> o.height,
        "distributionTxId" -> o.tx,
        "metadataBoxId" -> o.box,
        "miner" -> o.miner,
        "shareScore" -> o.share_score,
        "shareNum" -> o.share,
        "sharePercentage" -> o.share_perc,
        "minPay" -> Helpers.nanoErgToErg(o.minpay),
        "amountStored" -> Helpers.nanoErgToErg(o.stored),
        "amountPaid" -> Helpers.nanoErgToErg(o.paid),
        "amountAdded" -> Helpers.nanoErgToErg(o.change),
        "epochsMined" -> o.epochs_mined,
        "tokenDistributed" -> o.token,
        "tokenPaid" -> o.token_paid,
        "block" -> o.block,
        "created" -> o.created.toString
      )
    }
  }

  implicit val distResponseWrites: Writes[DistributionResponse] = new Writes[DistributionResponse] {
    override def writes(o: DistributionResponse): JsValue = {
      val members = o.nextMembers.map(m => Json.toJson(m))
      val states  = o.nextStates.map(s => Json.toJson(s))
      Json.obj( fields =
        "newMembers" -> members,
        "newStates" -> states
      )
    }
  }

  implicit val poolInformationWrites: Writes[PoolInformation] = new Writes[PoolInformation] {
    override def writes(o: PoolInformation): JsValue = {
      Json.obj( fields =
        "poolTag" -> o.poolTag,
        "title" -> o.title,
        "globalEpoch" -> o.g_epoch,
        "numSubPools" -> o.subpools,
        "lastBlock" -> o.last_block,
        "totalMembers" -> o.total_members,
        "maxMembers" -> o.max_members,
        "valueLocked" -> Helpers.nanoErgToErg(o.value_locked),
        "totalPaid" -> Helpers.nanoErgToErg(o.total_paid),
        "poolFees" -> (BigDecimal(o.fees) / PoolFees.POOL_FEE_CONST).toDouble,
        "currency" -> o.currency,
        "paymentType" -> o.payment_type,
        "epochsUntilKick" -> o.epoch_kick,
        "official" -> o.official,
        "creator" -> o.creator,
        "updated" -> o.updated.toString,
        "created" -> o.created.toString
      )
    }
  }

}
