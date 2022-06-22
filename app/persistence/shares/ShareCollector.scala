package persistence.shares

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.groups.entities.Member
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.payments.ShareStatistics
import io.getblok.subpooling_core.persistence.models.Models.{PartialShare, Share}
import io.getblok.subpooling_core.registers.MemberInfo
import models.DatabaseModels.SPoolBlock
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

  def addBlock(block: SPoolBlock): Unit = {

  }

  def merge(collector: ShareCollector): ShareCollector = {
    var mergeCount = 0L
    log.info("Now merging together intersected miners")
    log.info(s"Total of ${shareMap.size} miners in original map")
    log.info(s"Total of ${collector.shareMap.size} in map to merge")

    val differenceOne = shareMap.keys.toSeq.diff(collector.shareMap.keys.toSeq)
    val differenceTwo = collector.shareMap.keys.toSeq.diff(shareMap.keys.toSeq)

    log.info(s"Total of difference between original map and merge map: ${differenceOne.size}")
    log.info(s"Total of difference between merge map and original map: ${differenceTwo}")
    log.info(differenceOne.mkString("(", ", ", ")"))
    log.info(differenceTwo.mkString("(", ", ", ")"))

    for(miner <- shareMap.keys){
      if(collector.shareMap.contains(miner)) {
        val currentStats = shareMap(miner)
        val newStats = collector.shareMap(miner)
        currentStats.shareScore = currentStats.shareScore + newStats.shareScore
        currentStats.shareNum = currentStats.shareNum + newStats.shareNum
        currentStats.iterations = currentStats.iterations + newStats.iterations
        shareMap(miner) = currentStats
        mergeCount = mergeCount + 1
      }
    }

    log.info(s"A total of ${mergeCount} miners had stats merged into one another")
    var newCount = 0
    log.info("Now adding new miners")
    for(miner <- collector.shareMap.keys){
      if(!shareMap.contains(miner)){
        shareMap(miner) = collector.shareMap(miner)
        newCount = newCount + 1
      }
    }
    log.info(s"Added a total ${newCount} new miners")

    this
  }

  def avg(numCollectors: Int): ShareCollector = {
    for(miner <- shareMap.keys){
      val currentStats = shareMap(miner)
      currentStats.shareScore = currentStats.shareScore / numCollectors
      shareMap(miner) = currentStats
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
