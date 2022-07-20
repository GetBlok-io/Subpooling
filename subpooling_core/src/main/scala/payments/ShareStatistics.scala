package io.getblok.subpooling_core
package payments

import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PartialShare, Share}

class ShareStatistics(miner: String) {
  var shareNum:   BigDecimal   = 0L
  var shareScore: BigDecimal   = 0L
  var iterations: BigInt       = 0L
  val address:    String       = miner

  def addShare(sh: PartialShare): ShareStatistics = {
    shareNum    = shareNum + (sh.diff * AppParameters.shareConst)
    shareScore  = shareScore + (sh.diff * AppParameters.shareConst) / sh.netDiff
    iterations = iterations + 1
    this
  }

  /**
   * Used for batched SOLO pools, simply adds a constant value to the share score for each block,
   * thereby allowing for batched block rewards to be proportional to the miners who mined them
   */
  def addBlock(blockReward: Long): ShareStatistics = {
    shareNum    = shareNum + 1
    shareScore  = shareScore + blockReward / (Helpers.MinFee) // By making scores proportional to block rewards, we ensure SOLO miners get tx fees associated with their block
    iterations = iterations + 1
    this
  }

  def sharePercent(totalShareScore: BigDecimal): BigDecimal = {
    (shareScore * 100) / totalShareScore
  }

  def adjustedScore: BigDecimal = shareScore * AppParameters.scoreAdjustmentCoeff

  override def toString: String = {
    s"num: $shareNum \n score: $shareScore \n iterations: $iterations"
  }
}

object ShareStatistics{
  def apply(miner: String): ShareStatistics = {
    new ShareStatistics(miner)
  }
}


