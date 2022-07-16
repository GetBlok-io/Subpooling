package plasma_utils.payments

import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.Models.{MinerSettings, PoolInformation, PoolMember, PoolPlacement}
import io.getblok.subpooling_core.registers.MemberInfo
import models.DatabaseModels.SMinerSettings
import persistence.shares.ShareCollector
import utils.ConcurrentBoxLoader.BatchSelection

case class StandardProcessor(settings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection, reward: Long)
                            extends PaymentProcessor {

  def processNext(placements: Seq[PoolPlacement]): Seq[PoolPlacement] = {
    val members = collector.toMembers
    val totalScore = members.map(_.shareScore).sum

    val nextPlacements =  members.map{
      m =>
        val minPay = {
          if(batch.info.payment_type != PoolInformation.PAY_SOLO)
            settings.find(_.address == m.address.toString).map(p => (p.paymentthreshold * Helpers.OneErg).toLong)
          else
            Some(Helpers.MinFee)
        }
        val amountAdded = ((BigDecimal(m.shareScore) / totalScore) * reward).longValue()
        val lastPlacement = placements.find(_.miner == m.address.toString)

        PoolPlacement(
          batch.info.poolTag, 0L, batch.blocks.head.blockheight, "none", 0L, m.address.toString,
          m.shareScore, minPay.getOrElse(Helpers.MinFee * 10), lastPlacement.map(_.epochs_mined).getOrElse(1L),
          amountAdded, batch.blocks.last.gEpoch, batch.blocks.head.gEpoch
        )
    }
    nextPlacements
  }

  def processFirst(poolMembers: Seq[PoolMember]): Seq[PoolPlacement] = {
    val members = collector.toMembers
    val totalScore = members.map(_.shareScore).sum

    val nextPlacements =  members.map{
      m =>
        val minPay = settings.find(_.address == m.address.toString).map(p => (p.paymentthreshold * Helpers.OneErg).toLong)
        val amountAdded = ((BigDecimal(m.shareScore) / totalScore) * reward).longValue()
        val lastMember = poolMembers.find(_.miner == m.address.toString)

        PoolPlacement(
          batch.info.poolTag, 0L, batch.blocks.head.blockheight, "none", 0L, m.address.toString,
          m.shareScore, minPay.getOrElse(Helpers.MinFee * 10), lastMember.map(_.epochs_mined).getOrElse(1L),
          amountAdded, batch.blocks.last.gEpoch, batch.blocks.head.gEpoch
        )
    }
    nextPlacements
  }
}
