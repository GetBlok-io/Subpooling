package persistence.shares

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.SharesTable
import io.getblok.subpooling_core.persistence.models.Models.{PartialShare, PoolBlock}
import models.DatabaseModels.SPoolBlock
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
  logger.info(s"ShareHandler is using payment type ${paymentType.toString}")
  def queryToWindow(block: SPoolBlock, defaultTag: String): ShareCollector = {
    logger.info(s"Share handler querying to window for block ${block.blockheight} with creation date ${block.created}")
    var offset = 0
    val miners = Await.result(db.run(Tables.PoolSharesTable.queryPoolMiners(block.poolTag, defaultTag)), 60 seconds).map(m => m.address -> m.subpool).toMap
    while(collector.totalScore < AppParameters.pplnsWindow && offset != -1){
      val fShares = db.run(Tables.PoolSharesTable.queryBeforeDate( block.created, offset, SHARE_LIMIT))
      val shares = Await.result(fShares, 400 seconds).filter(sh => miners.contains(sh.miner))
      logger.info(s"${shares.size} shares were queried")
      logger.info(s"Share batch start: ${shares.head.created}")
      logger.info(s"Share batch end: ${shares.last.created}")
      logger.info(s"Share contains: ${shares.count(_.miner == "9faXjKXBSgQVwnH3TMpRaRC5mv39kr3SLdKZzEkBk9CRCkCEzjx")}")
      logger.info(s"Current offset: ${offset}")
      shares.foreach{
        s =>
          val ps = PartialShare(s.miner, s.difficulty, s.networkdifficulty, miners(s.miner))
          if(collector.totalScore < AppParameters.pplnsWindow){
            collector.addToMap(ps)
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
