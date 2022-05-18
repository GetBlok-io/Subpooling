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

  def sharePercent(totalShareScore: BigDecimal): BigDecimal = {
    (shareScore * 100) / totalShareScore
  }

  def adjustedScore: BigDecimal = shareScore * AppParameters.scoreAdjustmentCoeff

}

object ShareStatistics{
  def apply(miner: String): ShareStatistics = {
    new ShareStatistics(miner)
  }
}


