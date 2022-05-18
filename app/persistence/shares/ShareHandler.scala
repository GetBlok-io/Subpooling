package persistence.shares

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.SharesTable
import io.getblok.subpooling_core.persistence.models.Models.{PartialShare, PoolBlock}
import org.slf4j.LoggerFactory
import persistence.{PoolSharesTable, Tables}
import slick.jdbc.PostgresProfile

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ShareHandler(paymentType: PaymentType, blockMiner: String, db: PostgresProfile#Backend#Database){
  val collector: ShareCollector = new ShareCollector(paymentType, blockMiner)
  private val logger = LoggerFactory.getLogger("ShareHandler")
  final val SHARE_LIMIT = 50000

  def queryToWindow(block: PoolBlock, defaultTag: String): ShareCollector = {
    logger.info(s"Share handler querying to window for block ${block.blockheight}")
    var offset = 0
    while(collector.totalScore < AppParameters.pplnsWindow || offset == -1){
      val fShares = db.run(Tables.PoolSharesTable.queryBeforeDate(block.poolTag, defaultTag, block.created, offset, SHARE_LIMIT))
      val shares = Await.result(fShares, 20 seconds).map(s => PartialShare(s._1, s._2, s._3, s._4))
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
      if(shares.isEmpty)
        offset = -1
    }
    collector
  }


}
