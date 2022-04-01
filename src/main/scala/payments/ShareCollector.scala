package payments

import app.AppParameters
import org.ergoplatform.appkit.{Address, NetworkType}
import persistence.SharesTable
import persistence.models.Models.{DbConn, Share}
import registers.{MemberInfo, PropBytes, ShareDistribution}

import scala.collection.mutable
import scala.collection.mutable.Map
import scala.util.Try
class ShareCollector {
  val shareMap: mutable.Map[String, ShareStatistics] = mutable.Map.empty[String, ShareStatistics]
  implicit val networkType: NetworkType = AppParameters.networkType
  def addToMap(share: Share): ShareCollector = {
    if(shareMap.contains(share.miner)){
      shareMap(share.miner).addShare(share)
    }else{
      shareMap(share.miner) = ShareStatistics(share.miner).addShare(share)
    }
    this
  }

  def convertToDistribution: ShareDistribution = {
    shareMap.retain((a, s) => Try(Address.create(a)).isSuccess)
    val distMap = shareMap.map(a => PropBytes.ofAddress(Address.create(a._1)) -> new MemberInfo(Array(a._2.shareScore.longValue(), 0L, 0L, 0L, 0L)))
    new ShareDistribution(distMap.toMap)
  }
}
