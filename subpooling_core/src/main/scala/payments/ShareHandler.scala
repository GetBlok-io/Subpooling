package io.getblok.subpooling_core
package payments

import global.AppParameters

import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.SharesTable
import org.slf4j.LoggerFactory

class ShareHandler(shareTable: SharesTable, paymentType: PaymentType){
  val collector: ShareCollector = new ShareCollector(paymentType)
  private val logger = LoggerFactory.getLogger("ShareHandler")
  def queryToWindow(blockHeight: Long): ShareCollector = {
    logger.info(s"Share handler querying to window for block $blockHeight")
    var offset = 0
    while(collector.totalScore < AppParameters.pplnsWindow){
      val shares = shareTable.queryNext50k(blockHeight)
      logger.info(s"${shares.size} shares were queried")
      shares.foreach{
        s =>
          if(collector.totalScore < AppParameters.pplnsWindow){
            collector.addToMap(s)
          }
      }
      logger.info(s"Total collector score: ${collector.totalScore}")
      logger.info(s"Total shares: ${collector.totalShares}")
      logger.info(s"Total iterations: ${collector.totalIterations}")
      offset = offset + 50000
    }
    collector
  }


}
