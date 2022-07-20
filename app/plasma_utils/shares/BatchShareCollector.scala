package plasma_utils.shares

import configs.ParamsConfig
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolInformation
import models.DatabaseModels.SPoolBlock
import org.slf4j.{Logger, LoggerFactory}
import persistence.shares.{ShareCollector, ShareHandler}
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader.BatchSelection

import scala.concurrent.{ExecutionContext, Future}

class BatchShareCollector(batchSelection: BatchSelection, db: PostgresProfile#Backend#Database, params: ParamsConfig)
                         (implicit val ec: ExecutionContext) {
  private val logger: Logger = LoggerFactory.getLogger("BatchShareCollector")

  def batchCollect(): Future[ShareCollector] = {
    val poolTag = batchSelection.info.poolTag
    val block = batchSelection.blocks.head
    logger.info(s"Now querying shares, pool states, and miner settings for block ${block.blockheight}" +
      s" and pool ${block.poolTag}")

    val fCollectors = Future.sequence{

      if(batchSelection.info.payment_type != PoolInformation.PAY_PLASMA_SOLO){
        val collectors = for(shareBlock <- batchSelection.blocks) yield {
          val shareHandler = getShareHandler(shareBlock, batchSelection.info)
          Future(shareHandler.queryToWindow(shareBlock, params.defaultPoolTag))
        }
        collectors
      }
      else {
        logger.info(s"Performing SOLO query for block ${block.blockheight} with poolTag ${block.poolTag}" +
          s" and miner ${block.miner}")

        val collectors = (batchSelection.blocks.map{
          b =>
            val shareHandler = getShareHandler(block, batchSelection.info)
            Future(shareHandler.addForSOLO(b))
        })
        collectors
      }
    }

    val fCollector = {
      if(batchSelection.info.payment_type != PoolInformation.PAY_PLASMA_SOLO) {
        fCollectors.map {
          collectors =>
            val merged = collectors.slice(1, collectors.length).foldLeft(collectors.head) {
              (head: ShareCollector, other: ShareCollector) =>
                head.merge(other)
            }
            merged.avg(collectors.length)
        }
      }else{
        fCollectors.map {
          collectors =>
            val merged = collectors.slice(1, collectors.length).foldLeft(collectors.head) {
              (head: ShareCollector, other: ShareCollector) =>
                head.merge(other)
            }
            merged
        }
      }
    }
    fCollector
  }

  def getShareHandler(block: SPoolBlock, information: PoolInformation): ShareHandler = {
    information.payment_type match {
      case PoolInformation.PAY_PLASMA_PPLNS =>
        new ShareHandler(PaymentType.PPLNS_WINDOW, block.miner, db)
      case PoolInformation.PAY_PLASMA_SOLO =>
        new ShareHandler(PaymentType.SOLO_BATCH, block.miner, db) // TODO: Use solo batch payments
      case PoolInformation.PAY_EQ =>
        new ShareHandler(PaymentType.EQUAL_PAY, block.miner, db)
      case _ =>
        logger.warn(s"Could not find a payment type for pool ${information.poolTag}, defaulting to PPLNS Window")
        new ShareHandler(PaymentType.PPLNS_WINDOW, block.miner, db)
    }
  }
}
