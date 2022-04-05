package io.getblok.subpooling
package core.payments

import org.ergoplatform.appkit.{Address, NetworkType}
import core.persistence.models.Models.Share

import scala.collection.mutable
import scala.util.Try
import core.registers.{MemberInfo, PropBytes, ShareDistribution}
import global.AppParameters
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

  def toDistribution: ShareDistribution = {
    shareMap.retain((a, s) => Try(Address.create(a)).isSuccess)
    val distMap = shareMap.map(a => PropBytes.ofAddress(Address.create(a._1)) -> new MemberInfo(Array(a._2.adjustedScore.longValue(), 0L, 0L, 0L, 0L)))
    new ShareDistribution(distMap.toMap)
  }

  def totalScore:         BigDecimal = shareMap.map(s => s._2.shareScore).sum
  def totalShares:        BigDecimal = shareMap.map(s => s._2.shareNum).sum
  def totalAdjustedScore: BigDecimal = shareMap.map(s => s._2.adjustedScore).sum
}
