package payments
import app.AppParameters
import persistence.models.Models.Share

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

}

object ShareStatistics{
  def apply(miner: String): ShareStatistics = {
    new ShareStatistics(miner)
  }
}


