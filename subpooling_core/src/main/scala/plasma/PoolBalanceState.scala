package io.getblok.subpooling_core
package plasma

import java.time.LocalDateTime


case class PoolBalanceState(poolTag: String, gEpoch: Long, tx: String, digest: String, step: Int, command: String,
                            miner: String, minerHash: String, balance: Long, lastPaid: Long, block: Long,
                            created: LocalDateTime, updated: LocalDateTime) {
  def toStateValues: (PartialStateMiner, StateBalance) = {
    StateMiner(miner).toPartialStateMiner -> StateBalance(balance)
  }
}
