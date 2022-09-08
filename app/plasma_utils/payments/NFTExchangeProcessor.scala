package plasma_utils.payments

import actors.EmissionRequestHandler.{CalculateEmissions, ConstructCycle, EmissionResponse}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import io.getblok.subpooling_core.cycles.models.Cycle
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolInformation, PoolMember, PoolPlacement}
import models.DatabaseModels.SMinerSettings
import org.slf4j.{Logger, LoggerFactory}
import persistence.shares.ShareCollector
import utils.ConcurrentBoxLoader.BatchSelection

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

case class NFTExchangeProcessor(settings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection, reward: Long, fee: Long,
                                emReq: ActorRef)
                            extends PaymentProcessor {

  implicit val timeout: Timeout = Timeout(500 seconds)
  private val logger: Logger = LoggerFactory.getLogger("NFTExchangeProcessor")

  def processNext(placements: Seq[PoolPlacement]): Seq[PoolPlacement] = {

    val members = collector.toMembers


    val prePlacements =  members.map{
      m =>
        val minPay = {
          if(batch.info.payment_type != PoolInformation.PAY_PLASMA_SOLO)
            settings.find(_.address == m.address.toString).map(p => (p.paymentthreshold * Helpers.OneErg).toLong)
          else
            Some(Helpers.MinFee)
        }

        val lastPlacement = placements.find(_.miner == m.address.toString)

        PoolPlacement(
          batch.info.poolTag, 0L, batch.blocks.head.blockheight, "none", 0L, m.address.toString,
          m.shareScore, minPay.getOrElse(Helpers.MinFee * 10), lastPlacement.map(_.epochs_mined + 1).getOrElse(1L),
          0L, batch.blocks.last.gEpoch, batch.blocks.head.gEpoch, Some(0L)
        )
    }
    logger.info("Creating cycle")
    val cycle = Await.result((emReq ? ConstructCycle(batch, reward)).mapTo[Cycle], 500 seconds)
    logger.info("Created NFT Exchange cycle! Now calculating emissions")
    val emissionResponse = Await.result((emReq ? CalculateEmissions(cycle, prePlacements)).mapTo[EmissionResponse], 500 seconds)
    logger.info("Emission Results: ")
    logger.info(emissionResponse.results.toString)

    logger.info(s"A total of ${emissionResponse.nextPlacements.length} placements were returned!")
    emissionResponse.nextPlacements
  }

  def processFirst(poolMembers: Seq[PoolMember]): Seq[PoolPlacement] = {
    val members = collector.toMembers

    val prePlacements =  members.map{
      m =>
        val minPay = settings.find(_.address == m.address.toString).map(p => (p.paymentthreshold * Helpers.OneErg).toLong)

        val lastMember = poolMembers.find(_.miner == m.address.toString)

        PoolPlacement(
          batch.info.poolTag, 0L, batch.blocks.head.blockheight, "none", 0L, m.address.toString,
          m.shareScore, minPay.getOrElse(Helpers.MinFee * 10), lastMember.map(_.epochs_mined + 1).getOrElse(1L),
          0L, batch.blocks.last.gEpoch, batch.blocks.head.gEpoch, Some(0L)
        )
    }

    logger.info("Creating cycle")
    val cycle = Await.result((emReq ? ConstructCycle(batch, reward)).mapTo[Cycle], 500 seconds)
    logger.info("Created NFT Exchange cycle! Now calculating emissions")
    val emissionResponse = Await.result((emReq ? CalculateEmissions(cycle, prePlacements)).mapTo[EmissionResponse], 500 seconds)
    logger.info("Emission Results: ")
    logger.info(emissionResponse.results.toString)

    logger.info(s"A total of ${emissionResponse.nextPlacements.length} placements were returned!")
    emissionResponse.nextPlacements
  }
}
