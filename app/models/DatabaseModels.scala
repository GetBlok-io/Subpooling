package models

import akka.parboiled2.RuleTrace.StringMatch
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
  case class SBlock(id: Long, blockheight: Long, netDiff: Double, status: String, confirmation: Double, effort: Option[Double], txConfirmation: Option[String],
                        miner: String, reward: Double, hash: Option[String], created: LocalDateTime){
    def getNanoErgReward: Long = (BigDecimal(reward) * Parameters.OneErg).longValue()

    def asPoolBlock: SPoolBlock = {
      SPoolBlock(id, blockheight, netDiff, status, confirmation, effort, txConfirmation, miner, reward, hash, created, "c6b75f607ff08d76ae1acb78564e5b928ccd96b8b4dbcad161d6206db7e608c0", -1, created)
    }
  }

  case class StateHistory(poolTag: String, gEpoch: Long, box: String, tx: String, commandBox: String, command: String,
                          status: String, step: Int, digest: String, manifest: String,
                          subTree_1: String, subTree_2: String, subTree_3: String, subTree_4: String,
                          block: Long, created: LocalDateTime, updated: LocalDateTime)

  case class NodeAsset(tokenId: String, boxId: String, headerId: String, index: Int, value: Long)
  case class NodeInput(boxId: String, txId: String, headerId: String, proofBytes: Option[String], extension: String,
                       index: Int, mainChain: Boolean)
  case class NodeOutput(boxId: String, txId: String, headerId: String, value: Long, address: String)
}
