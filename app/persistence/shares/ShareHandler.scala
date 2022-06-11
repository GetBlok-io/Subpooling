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
    val miners = Await.result(db.run(Tables.PoolSharesTable.queryMinerPools), 100 seconds).toMap

    while(collector.totalScore < AppParameters.pplnsWindow && offset != -1){

      val fShares = db.run(Tables.PoolSharesTable.queryBeforeDate( block.created, offset, SHARE_LIMIT))
      val shares = Await.result(fShares, 400 seconds).filter{
        sh =>
          if(block.poolTag != defaultTag) {
            miners.get(sh.miner).flatten.getOrElse(defaultTag) == block.poolTag
          }else{
            miners.get(sh.miner).flatten.getOrElse(defaultTag) == defaultTag
          }
      }
      logger.info(s"${shares.size} shares were queried")
      logger.info(s"Share batch start: ${shares.headOption.map(_.created)}")
      logger.info(s"Share batch end: ${shares.lastOption.map(_.created)}")
      logger.info(s"Current offset: ${offset}")
      shares.foreach{
        s =>
          val ps = PartialShare(s.miner, s.difficulty, s.networkdifficulty, Some(miners.get(s.miner).flatten.getOrElse(defaultTag)))
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
      if(offset >= 400000)
        offset = -1
    }
    collector
  }

  def queryForSOLO(block: SPoolBlock, defaultTag: String): ShareCollector = {

    logger.info(s"Share handler querying for SOLO on block ${block.blockheight} with creation date ${block.created}")
    var offset = 0

    val fShares = db.run(Tables.PoolSharesTable.queryBeforeDate( block.created, offset, SHARE_LIMIT))
    val shares = Await.result(fShares, 400 seconds).filter{
      sh =>
        sh.miner == blockMiner
    }
    logger.info(s"${shares.size} shares were queried")
    logger.info(s"Share batch start: ${shares.head.created}")
    logger.info(s"Share batch end: ${shares.last.created}")
    logger.info(s"Current offset: ${offset}")
    shares.foreach{
      s =>
        val ps = PartialShare(s.miner, s.difficulty, s.networkdifficulty, Some(block.poolTag))
        if(collector.totalScore < AppParameters.pplnsWindow){
          collector.addToMap(ps)
        }
    }
    logger.info(s"Total collector score: ${collector.totalScore}")
    logger.info(s"Total shares: ${collector.totalShares}")
    logger.info(s"Total iterations: ${collector.totalIterations}")
    logger.info(s"Total adjusted score: ${collector.totalAdjustedScore}")
    collector
  }


}
