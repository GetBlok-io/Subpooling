package models

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

  case class SMinerSettings(address: String, paymentthreshold: Double, created: LocalDateTime, updated: LocalDateTime,
                           subpool: Option[String])
}
