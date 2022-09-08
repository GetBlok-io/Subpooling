package actors

import actors.EmissionRequestHandler.{CalculateEmissions, ConstructCycle, CycleEmissions, CycleResponse, EmissionRequest, EmissionResponse}
import actors.StateRequestHandler._
import akka.actor.{Actor, Props}
import configs.{Contexts, ExplorerConfig, NodeConfig}
import io.getblok.subpooling_core.cycles.models.{Cycle, CycleResults, CycleState, EmissionResults}
import io.getblok.subpooling_core.explorer.ExplorerHandler
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.PersistenceModels._
import io.getblok.subpooling_core.plasma.{BalanceState, SingleBalance, StateBalance}
import io.getblok.subpooling_core.states.models.PlasmaMiner
import org.ergoplatform.appkit._
import plasma_utils.UntrackedPoolStateException
import plasma_utils.payments.PaymentRouter
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}
import utils.ConcurrentBoxLoader.BatchSelection

import javax.inject.Inject
import scala.util.{Failure, Success, Try}

class EmissionRequestHandler @Inject()(config: Configuration, ws: WSClient) extends Actor{

  private val nodeConfig                  = new NodeConfig(config)
  private val explorerConfig              = new ExplorerConfig(config)
  private val ergoClient: ErgoClient      = nodeConfig.getClient
  private val wallet:     NodeWallet      = nodeConfig.getNodeWallet
  private val explorer:   ExplorerHandler = explorerConfig.explorerHandler
  private val logger:     Logger     = Logger("EmissionsRequestHandler")

  logger.info("Initiating EmissionsRequestHandler")

  override def receive: Receive = {
    case emitReq: EmissionRequest =>
        Try {
          logger.info("Received emission request")
          emitReq match {
            case ConstructCycle(batch, reward) =>
              ergoClient.execute{
                ctx =>
                  logger.info("Now constructing cycle")
                  sender ! PaymentRouter.routeCycle(ctx, wallet, reward, batch, explorer, Some(ws), Some(context.dispatcher))
              }
            case CalculateEmissions(cycle, placements) =>
              logger.info("Now calculating emissions")
              val emissionResults = cycle.simulateSwap

              val nextPlacements = cycle.morphPlacementValues(placements, emissionResults)

              sender ! EmissionResponse(emissionResults, nextPlacements)
            case CycleEmissions(cycle, placements, inputs) =>
              logger.info("Now executing emission cycle")
              val cycleState = makeCycleState(cycle, inputs, placements)
              logger.info("Cycle state created!")
              val emissionResults = cycle.simulateSwap
              logger.info("Simulated swap, now cycling!")
              val cycleResults = Try(cycle.cycle(cycleState, emissionResults, AppParameters.sendTxs))
                .recoverWith{
                  case e: Throwable =>
                    logger.error("Critical error while cycling emissions!", e)
                    Failure(e)
                }.get
              logger.info("Cycle successful, now morphing placements")
              val nextPlacements = cycle.morphPlacementHolding(
                cycle.morphPlacementValues(placements, emissionResults),
                cycleResults.nextHoldingBox
              )

              sender ! CycleResponse(cycleResults, nextPlacements)
          }
        }
  }


  def makeCycleState(cycle: Cycle, inputs: Seq[InputBox], placements: Seq[PoolPlacement]): CycleState = {
    val emBox = cycle.getEmissionsBox
    val cycleState = CycleState(emBox, inputs, placements)
    cycleState
  }
}


object EmissionRequestHandler {
  def props: Props = Props[EmissionRequestHandler]
  trait EmissionRequest

  case class ConstructCycle(batch: BatchSelection, reward: Long) extends EmissionRequest
  case class CalculateEmissions(cycle: Cycle, placements: Seq[PoolPlacement]) extends EmissionRequest
  case class CycleEmissions(cycle: Cycle, placements: Seq[PoolPlacement], inputs: Seq[InputBox]) extends EmissionRequest

  case class EmissionResponse(results: EmissionResults, nextPlacements: Seq[PoolPlacement])
  case class CycleResponse(cycleResults: CycleResults, nextPlacements: Seq[PoolPlacement])
}




