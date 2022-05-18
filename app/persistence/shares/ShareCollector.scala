package persistence.shares

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.groups.entities.Member
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.payments.ShareStatistics
import io.getblok.subpooling_core.persistence.models.Models.{PartialShare, Share}
import io.getblok.subpooling_core.registers.MemberInfo
import org.ergoplatform.appkit.{Address, NetworkType}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.Try
class ShareCollector(paymentType: PaymentType, blockMiner: String) {
  val shareMap: mutable.Map[String, ShareStatistics] = mutable.Map.empty[String, ShareStatistics]
  implicit val networkType: NetworkType = AppParameters.networkType
  val log = LoggerFactory.getLogger("ShareCollector")

  def addToMap(share: PartialShare): ShareCollector = {
    if(shareMap.contains(share.miner)){
      shareMap(share.miner).addShare(share)
    }else{
      log.info(s"Adding new miner to shareMap ${share.miner}")
      shareMap(share.miner) = new ShareStatistics(share.miner).addShare(share)
    }
    this
  }

  def toMembers: Array[Member] = {
    shareMap.retain((a, s) => Try(Address.create(a)).isSuccess)
    paymentType match {
      case PaymentType.PPLNS_WINDOW =>
        val members = shareMap.map(a => Member(Address.create(a._1), new MemberInfo(Array(a._2.adjustedScore.longValue(), 0L, 0L, 0L, 0L))))
        members.toArray
      case PaymentType.PPS_WINDOW =>
        val members = shareMap.map(a => Member(Address.create(a._1), new MemberInfo(Array(a._2.shareNum.longValue(), 0L, 0L, 0L, 0L))))
        members.toArray
      case PaymentType.PPS_NUM_WINDOW =>
        val members = shareMap.map(a => Member(Address.create(a._1), new MemberInfo(Array(a._2.iterations.longValue(), 0L, 0L, 0L, 0L))))
        members.toArray
      case PaymentType.EQUAL_PAY =>
        val members = shareMap.map(a => Member(Address.create(a._1), new MemberInfo(Array(10000L, 0L, 0L, 0L, 0L))))
        members.toArray
      case PaymentType.SOLO_SHARES =>
        val memberInfo = new MemberInfo(Array(shareMap.filter(_._1 == blockMiner).head._2.adjustedScore.longValue(), 0L, 0L, 0L, 0L))
        val minerMember = Member(Address.create(blockMiner), memberInfo)
        Array(minerMember)
      case _ =>
        // Match to PPLNS_WINDOW for default
        val members = shareMap.map(a => Member(Address.create(a._1), new MemberInfo(Array(a._2.adjustedScore.longValue(), 0L, 0L, 0L, 0L))))
        members.toArray
    }



  }

  def totalScore:         BigDecimal = shareMap.map(s => s._2.shareScore).sum
  def totalShares:        BigDecimal = shareMap.map(s => s._2.shareNum).sum
  def totalAdjustedScore: BigDecimal = shareMap.map(s => s._2.adjustedScore).sum
  def totalIterations:    BigInt     = shareMap.map(s => s._2.iterations).sum
}
