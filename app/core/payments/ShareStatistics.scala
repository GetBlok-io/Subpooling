package io.getblok.subpooling
package core.payments

import ShareStatistics.ADJUSTMENT_COEFFICIENT
import core.persistence.models.Models.Share
import global.AppParameters

class ShareStatistics(miner: String) {
  var shareNum:   BigDecimal   = 0L
  var shareScore: BigDecimal   = 0L
  val address:    String       = miner

  def addShare(sh: Share): ShareStatistics = {
    shareNum    = shareNum + (sh.difficulty * AppParameters.shareConst)
    shareScore  = shareScore + (sh.difficulty * AppParameters.shareConst) / sh.networkdifficulty
    this
  }

  def sharePercent(totalShareScore: BigDecimal): BigDecimal = {
    (shareScore * 100) / totalShareScore
  }

  def adjustedScore: BigDecimal = shareScore * ADJUSTMENT_COEFFICIENT

}

object ShareStatistics{
  def apply(miner: String): ShareStatistics = {
    new ShareStatistics(miner)
  }

  val ADJUSTMENT_COEFFICIENT = 10000000L
}


