package io.getblok.subpooling_core
package payments

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.persistence.models.Models.Share

class ShareStatistics(miner: String) {
  var shareNum:   BigDecimal   = 0L
  var shareScore: BigDecimal   = 0L
  var iterations: BigInt       = 0L
  val address:    String       = miner

  def addShare(sh: Share): ShareStatistics = {
    shareNum    = shareNum + (sh.difficulty * AppParameters.shareConst)
    shareScore  = shareScore + (sh.difficulty * AppParameters.shareConst) / sh.networkdifficulty
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


