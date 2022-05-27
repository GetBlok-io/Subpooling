package models

import org.ergoplatform.appkit.Parameters

import java.time.LocalDateTime

object DatabaseModels {
  case class BalanceChange(poolTag: String, address: String, coin: String,
                           amount: Double, tx: String, tokens: Option[String],
                           created: LocalDateTime, block: Long, gEpoch: Long)

  case class Balance(poolId: String, address: String, amount: Double,
                     created: LocalDateTime, updated: LocalDateTime)

  case class Payment(poolTag: String, address: String, coin: String,
                     amount: Double, tx: String, tokens: Option[String],
                     created: LocalDateTime, block: Long, gEpoch: Long)

  case class ChangeKeys(poolTag: String, block: Long, gEpoch: Long)

  case class MinerStats(id: Long, poolId: String, miner: String, worker: String, hashrate: Double, sharespersecond: Double,
                        created: LocalDateTime)

  case class SMinerSettings(poolId: String, address: String, paymentthreshold: Double, created: LocalDateTime, updated: LocalDateTime,
                           subpool: Option[String])
  case class SPoolBlock(id: Long, blockheight: Long, netDiff: Double, status: String, confirmation: Double, effort: Option[Double], txConfirmation: Option[String],
                       miner: String, reward: Double, hash: Option[String], created: LocalDateTime, poolTag: String, gEpoch: Long, updated: LocalDateTime){
    def getNanoErgReward: Long = (BigDecimal(reward) * Parameters.OneErg).longValue()
  }
}
