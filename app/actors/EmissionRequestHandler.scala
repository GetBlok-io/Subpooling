package actors

import actors.EmissionRequestHandler.{CalculateEmissions, ConstructCycle, EmissionRequest, EmissionResponse}
import actors.StateRequestHandler._
import akka.actor.{Actor, Props}
import configs.{ExplorerConfig, NodeConfig}
import io.getblok.subpooling_core.cycles.models.{Cycle, EmissionResults}
import io.getblok.subpooling_core.explorer.ExplorerHandler
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.PersistenceModels._
import io.getblok.subpooling_core.plasma.{BalanceState, SingleBalance, StateBalance}
import io.getblok.subpooling_core.states.models.PlasmaMiner
import org.ergoplatform.appkit._
import plasma_utils.UntrackedPoolStateException
import plasma_utils.payments.PaymentRouter
import play.api.{Configuration, Logger}
import utils.ConcurrentBoxLoader.BatchSelection

import javax.inject.Inject
import scala.util.{Failure, Success, Try}

class EmissionRequestHandler @Inject()(config: Configuration) extends Actor{

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
          emitReq match {
            case ConstructCycle(batch, reward) =>
              ergoClient.execute{
                ctx =>
                  sender ! PaymentRouter.routeCycle(ctx, wallet, reward, batch, explorer)
              }
            case CalculateEmissions(cycle, placements) =>
              val emissionResults = cycle.simulateSwap

              val nextPlacements = cycle.morphPlacementValues(placements, emissionResults)

              sender ! EmissionResponse(emissionResults, nextPlacements)
          }
        }
  }
}


object EmissionRequestHandler {
  def props: Props = Props[EmissionRequestHandler]
  trait EmissionRequest

  case class ConstructCycle(batch: BatchSelection, reward: Long) extends EmissionRequest
  case class CalculateEmissions(cycle: Cycle, placements: Seq[PoolPlacement]) extends EmissionRequest
  case class CycleEmissions(cycle: Cycle, placements: Seq[PoolPlacement]) extends EmissionRequest

  case class EmissionResponse(results: EmissionResults, nextPlacements: Seq[PoolPlacement])
}




