package models

import actors.GroupRequestHandler.DistributionResponse
import io.getblok.subpooling_core.global.Helpers
import play.api.libs.json.{JsObject, JsResult, JsSuccess, JsValue, Json, Reads, Writes}
import io.getblok.subpooling_core.persistence.models.PersistenceModels._
import io.getblok.subpooling_core.registers.PoolFees
import models.DatabaseModels.{BalanceChange, ChangeKeys, Payment, SPoolBlock}

import java.time.LocalDateTime
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

  implicit val blockWrites: Writes[PoolBlock] = new Writes[PoolBlock] {
    override def writes(o: PoolBlock): JsValue = {
      Json.obj( fields =
        "id" -> o.id,
        "blockHeight" -> o.blockheight,
        "poolTag" -> o.poolTag,
        "gEpoch" -> o.gEpoch,
        "status" -> o.status,
        "miner" -> o.miner,
        "reward" -> o.reward,
        "confirmation" -> o.confirmation,
        "nonce" -> o.txConfirmation,
        "hash" -> o.hash,
        "effort" -> o.effort,
        "networkDifficulty" -> o.netDiff,
        "created" -> o.created.toString,
        "updated" -> o.updated
      )
    }
  }

  implicit val sblockWrites: Writes[SPoolBlock] = new Writes[SPoolBlock] {
    override def writes(o: SPoolBlock): JsValue = {
      Json.obj( fields =
        "id" -> o.id,
        "blockHeight" -> o.blockheight,
        "poolTag" -> o.poolTag,
        "gEpoch" -> o.gEpoch,
        "status" -> o.status,
        "miner" -> o.miner,
        "reward" -> o.reward,
        "confirmation" -> o.confirmation,
        "nonce" -> o.txConfirmation,
        "hash" -> o.hash,
        "effort" -> o.effort,
        "networkDifficulty" -> o.netDiff,
        "created" -> o.created.toString,
        "updated" -> o.updated
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
        "minPay" -> o.minpay,
        "epochsMined" -> o.epochs_mined,
        "amountAdded" -> o.amount,

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
        "minPay" -> o.minpay,
        "amountStored" -> o.stored,
        "amountPaid" -> o.paid,
        "amountAdded" -> o.change,
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
        "blocksFound" -> o.blocksFound,
        "totalMembers" -> o.total_members,
        "maxMembers" -> o.max_members,
        "valueLocked" -> o.value_locked,
        "totalPaid" -> o.total_paid,
        "poolFees" -> (BigDecimal(o.fees) / PoolFees.POOL_FEE_CONST).toDouble,
        "currency" -> o.currency,
        "paymentType" -> o.payment_type,
        "epochsUntilKick" -> o.epoch_kick,
        "official" -> o.official,
        "creator" -> o.creator,
        "updated" -> o.updated.toString,
        "created" -> o.created.toString,
        "emissionsId" -> o.emissions_id,
        "emissionsType" -> o.emissions_type
      )
    }
  }

  case class PagedResponse(numPages: Int, totalValues: Long, payload: JsValue)
  implicit val writesPagedResponse: Writes[PagedResponse] = new Writes[PagedResponse] {
    override def writes(o: PagedResponse): JsValue = {
      Json.obj( fields =
        "numPages" -> o.numPages,
        "totalValues" -> o.totalValues,
        "payload" -> o.payload
      )
    }
  }

  implicit val writesChangeKeys: Writes[ChangeKeys] = new Writes[ChangeKeys] {
    override def writes(o: ChangeKeys): JsValue = {
      Json.obj( fields =
        "poolTag" -> o.poolTag,
        "block" -> o.block,
        "gEpoch" -> o.gEpoch
      )
    }
  }

  case class MinerResponse(poolTag: String, minPay: Double, pending: Double, owed: Double, avg: Double, lastMemberInfo: Option[PoolMember])

  implicit val writesMinerResponse: Writes[MinerResponse] = new Writes[MinerResponse] {
    override def writes(o: MinerResponse): JsValue = {
      Json.obj( fields =
        "poolTag" -> o.poolTag,
        "minPay" -> o.minPay,
        "pendingBalance" -> o.pending,
        "owedBalance" -> o.owed,
        "avgDelta" -> o.avg,
        "lastMemberInfo" -> Json.toJson(o.lastMemberInfo)
      )
    }
  }

  implicit val writesPayments: Writes[Payment] = new Writes[Payment] {
    override def writes(o: Payment): JsValue = {
      Json.obj( fields =
        "address" -> o.address,
        "coin" -> o.coin,
        "amount" -> o.amount,
        "txId" -> o.tx,
        "tokens" -> o.tokens.getOrElse("").toString,
        "poolTag" -> o.poolTag,
        "gEpoch" -> o.gEpoch,
        "block" -> o.block,
        "created" -> o.created
      )
    }
  }

  implicit val writesBalanceChange: Writes[BalanceChange] = new Writes[BalanceChange] {
    override def writes(o: BalanceChange): JsValue = {
      Json.obj( fields =
        "address" -> o.address,
        "coin" -> o.coin,
        "amount" -> o.amount,
        "txId" -> o.tx,
        "tokens" -> o.tokens.getOrElse("").toString,
        "poolTag" -> o.poolTag,
        "gEpoch" -> o.gEpoch,
        "block" -> o.block,
        "created" -> o.created
      )
    }
  }
  case class Earnings(address: String, coin: String, amount: Double, date: LocalDateTime)
  implicit val writesEarnings: Writes[Earnings] = new Writes[Earnings] {
    override def writes(o: Earnings): JsValue = {
      Json.obj( fields =
        "address" -> o.address,
        "coin" -> o.coin,
        "amount" -> o.amount,
        "date" -> o.date
      )
    }
  }

  case class PoolStatistics(poolTag: String, hashrate: Double, sharesPerSecond: Double, effort: Option[Double])
  implicit val writesPoolStats: Writes[PoolStatistics] = new Writes[PoolStatistics] {
    override def writes(o: PoolStatistics): JsValue = {
      Json.obj( fields =
        "poolTag" -> o.poolTag,
        "hashrate" -> o.hashrate,
        "sharesPerSecond" -> o.sharesPerSecond,
        "effort" -> o.effort
      )
    }
  }
  case class PayoutSettings(ip: String, minPay: Double)
  implicit val readsSettingsReq: Reads[PayoutSettings] = new Reads[PayoutSettings] {
    override   def reads(json: JsValue): JsResult[PayoutSettings] = {
      val ip = (json \ "IpAddress").as[String]
      val minPay = (json \ "MinPay").as[Double]
      JsSuccess(PayoutSettings(ip, minPay))
    }
  }

  case class SubPoolSettings(ip: String, subPool: String)
  implicit val readsSubPoolSettingsReq: Reads[SubPoolSettings] = new Reads[SubPoolSettings] {
    override   def reads(json: JsValue): JsResult[SubPoolSettings] = {
      val ip = (json \ "IpAddress").as[String]
      val subpool = (json \ "SubPool").as[String]
      JsSuccess(SubPoolSettings(ip, subpool))
    }
  }


  object Paginate {
    def apply[T](writeable: Seq[T], pageSize: Option[Int])(implicit write: Writes[T]): PagedResponse ={

      PagedResponse(pageSize.get, writeable.length, Json.toJson(writeable.take(pageSize.get)))
    }
  }

  object Intervals {
    final val HOURLY  = "hour"
    final val DAILY   = "day"
    final val MONTHLY = "month"
    final val YEARLY  = "year"

  }
}
