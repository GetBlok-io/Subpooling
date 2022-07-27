package actors

import actors.StateRequestHandler._
import akka.actor.{Actor, Props}
import configs.NodeConfig
import io.getblok.subpooling_core.boxes.{EmissionsBox, ExchangeEmissionsBox, MetadataInputBox, ProportionalEmissionsBox}
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.emissions.{EmissionsContract, ExchangeContract, ProportionalEmissionsContract}
import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, HoldingContract, SimpleHoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.builders.{DistributionBuilder, HoldingBuilder}
import io.getblok.subpooling_core.groups.entities.{Member, Pool, Subpool}
import io.getblok.subpooling_core.groups.models.{GroupBuilder, GroupSelector, TransactionGroup, TransactionStage}
import io.getblok.subpooling_core.groups.selectors.{LoadingSelector, SelectionParameters, StandardSelector}
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager, HoldingGroup}
import io.getblok.subpooling_core.persistence.models.PersistenceModels._
import io.getblok.subpooling_core.plasma.{BalanceState, PoolBalanceState, SingleBalance, StateBalance}
import io.getblok.subpooling_core.states.groups.StateGroup
import io.getblok.subpooling_core.states.models.{PlasmaMiner, TransformResult}
import models.DatabaseModels.SPoolBlock
import org.ergoplatform.appkit._
import plasma_utils.UntrackedPoolStateException
import plasma_utils.payments.PaymentRouter
import play.api.{Configuration, Logger}
import utils.ConcurrentBoxLoader.BatchSelection
import utils.EmissionTemplates

import javax.inject.Inject
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

class StateRequestHandler @Inject()(config: Configuration) extends Actor{

  private val nodeConfig             = new NodeConfig(config)
  private val ergoClient: ErgoClient = nodeConfig.getClient
  private val wallet:     NodeWallet = nodeConfig.getNodeWallet
  private val logger:     Logger     = Logger("StateRequestHandler")
  logger.info("Initiating StateRequestHandler")

  override def receive: Receive = {
    case stateReq: StateRequest =>
        Try {
          stateReq match {
            case DistConstructor(poolState, inputBoxes, batch, balanceState, placements) =>
              Try{
                ergoClient.execute{
                  ctx =>
                    val poolBox = grabPoolBox(ctx, poolState, balanceState)
                    val plasmaMiners = morphToPlasma(placements, balanceState, batch.blocks.head.netDiff)

                    val stateGroup = PaymentRouter.routeStateGroup(ctx, wallet, batch, poolBox, plasmaMiners, inputBoxes)
                    sender ! ConstructedDist(stateGroup, poolState)
                }
              }.recoverWith{
                case untrackedPoolStateException: UntrackedPoolStateException =>
                  logger.error("An untracked pool state was found!", untrackedPoolStateException)
                  sender ! StateFailure(untrackedPoolStateException)
                  Failure(untrackedPoolStateException)
                case ex: Exception =>
                  logger.error("An unknown exception was thrown during DistConstruction!", ex)
                  sender ! StateFailure(ex)
                  Failure(ex)
              }
            case ExecuteDist(constDist, sendTxs) =>
              val stateGroup = constDist.stateGroup
              logger.info("Now setting up StateGroup")
              stateGroup.setup()

              logger.info("Now applying transformations!")
              val tryTransform = stateGroup.applyTransformations()

              tryTransform match {
                case Success(value) =>
                  logger.info("Successfully applied transforms!")
                case Failure(exception) =>
                  logger.error("There was a fatal error while applying transforms!", exception)
                  sender ! StateFailure(exception)
              }

              val transforms = {
                if(sendTxs){
                  logger.info("Now sending transactions!")
                  stateGroup.sendTransactions
                }else{
                  logger.info("Skipped transaction sending...")
                  stateGroup.transformResults
                }
              }
              logger.info("Making group members!")

              val members = stateGroup.getMembers
              val poolBalanceStates = stateGroup.getPoolBalanceStates
              logger.info("Now sending DistResponse to sender!")
              sender ! DistResponse(transforms, members, poolBalanceStates, constDist.poolState)
          }
        }.recoverWith{
          case ex =>
            logger.error("There was a fatal exception thrown by this StateRequestHandler!", ex)
            Failure(ex)
        }
  }

  def grabPoolBox[T <: StateBalance](ctx: BlockchainContext, poolState: PoolState, balanceState: BalanceState[T]): PoolBox[T] = {
    val poolTag = poolState.subpool
    val box = Try{ctx.getBoxesById(poolState.box).head}

    PoolBox(box.getOrElse(throw new UntrackedPoolStateException(poolState.box, poolTag)), balanceState)
  }

  def morphToPlasma[T <: StateBalance](placements: Seq[PoolPlacement], balanceState: BalanceState[T], netDiff: Double): Seq[PlasmaMiner] = {
    val totalScore = placements.map(_.score).sum

    val partialPlasmaMiners = placements.map{
      p =>
        val sharePerc: Double = (BigDecimal(p.score) / totalScore).toDouble
        val shareNum: Long = (p.score * BigDecimal(netDiff)).toLong
        PlasmaMiner(Address.create(p.miner), p.score, 0L, p.amount, p.minpay, sharePerc, shareNum, p.epochs_mined)
    }
    val balState = balanceState.asInstanceOf[BalanceState[SingleBalance]]

    balanceState.map.initiate()
    val balances = partialPlasmaMiners zip balState.map.lookUp(partialPlasmaMiners.map(_.toStateMiner.toPartialStateMiner):_*).response
    balanceState.map.dropChanges()
    val plasmaMiners = balances.map(b => b._1.copy(balance = b._2.tryOp.get.map(_.balance).getOrElse(0L)))
      .sortBy(m => BigInt(m.toStateMiner.toPartialStateMiner.bytes))
    plasmaMiners
  }

}

object StateRequestHandler {
  def props: Props = Props[StateRequestHandler]
  trait StateRequest

  case class DistConstructor[T <: StateBalance](poolState: PoolState, inputBoxes: Seq[InputBox],
                             batch: BatchSelection, balanceState: BalanceState[T], placements: Seq[PoolPlacement]) extends StateRequest
  case class ConstructedDist[T <: StateBalance](stateGroup: StateGroup[T], poolState: PoolState)
  case class ExecuteDist[T <: StateBalance](constDist: ConstructedDist[T], sendTxs: Boolean) extends StateRequest
  case class DistResponse[T <: StateBalance](transforms: Seq[Try[TransformResult[T]]],
                                             members: Seq[PoolMember], poolBalanceStates: Seq[PoolBalanceState], nextState: PoolState)

  case class PoolBox[T <: StateBalance](box: InputBox, balanceState: BalanceState[T])

  case class StateFailure(ex: Throwable)

}


