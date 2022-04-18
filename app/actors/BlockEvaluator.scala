package actors

import actors.BlockEvaluator.{EvaluateConfirmedBlock, EvaluateInitiatedBlock, EvaluatePendingBlock}
import actors.GroupRequestHandler.{DistributionResponse, ExecuteDistribution, PoolData}
import akka.actor.{Actor, Props}
import configs.NodeConfig
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, SimpleHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.builders.DistributionBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.LoadingSelector
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager}
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolMember, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, ErgoId, NetworkType}
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer

/**
 * Block evaluator, which checks pending, and confirmed blocks to move them to their next stages (confirmed, and initiated).
 * Serves as a middleman to allow piped responses to other actors in the system.
 */
class BlockEvaluator @Inject()(config: Configuration) extends Actor{

  private val logger:     Logger     = Logger("BlockEvaluator")
  logger.info("Initiating BlockEvaluator")

  override def receive: Receive = {
    case EvaluatePendingBlock(block) =>


    case EvaluateConfirmedBlock(block) =>

    case EvaluateInitiatedBlock(block) =>


  }

}

object BlockEvaluator {
  def props: Props = Props[BlockEvaluator]

  sealed trait BlockEvaluationRequest

  case class EvaluatePendingBlock(block: Block)     extends BlockEvaluationRequest
  case class EvaluateConfirmedBlock(block: Block)   extends BlockEvaluationRequest
  case class EvaluateInitiatedBlock(block: Block)   extends BlockEvaluationRequest

  case class UpdateBlockResponse(block: Block, status: String)
}


