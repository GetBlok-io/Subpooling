package io.getblok.subpooling_core
package payments

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.persistence.models.Models.{PartialShare, Share}

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
  def addBlock(): ShareStatistics = {
    shareNum    = shareNum + 100
    shareScore  = shareScore + 100
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


