package plasma_utils.payments

import actors.StateRequestHandler.PoolBox
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.Models.{MinerSettings, PoolInformation}
import io.getblok.subpooling_core.states.groups.{PayoutGroup, StateGroup}
import io.getblok.subpooling_core.states.models.PlasmaMiner
import models.DatabaseModels.SMinerSettings
import org.ergoplatform.appkit.{BlockchainContext, InputBox}
import persistence.shares.ShareCollector
import utils.ConcurrentBoxLoader.BatchSelection

object PaymentRouter {

  def routeProcessor(info: PoolInformation, settings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection, reward: Long): PaymentProcessor = {
    info.currency match {
      case PoolInformation.CURR_ERG =>
        StandardProcessor(settings, collector, batch, reward)
      case _ =>
        throw new Exception("Unsupported currency found during processor routing!")
    }
  }

  def routeStateGroup(ctx: BlockchainContext, wallet: NodeWallet, batch: BatchSelection,
                      poolBox: PoolBox, miners: Seq[PlasmaMiner], inputBoxes: Seq[InputBox]): StateGroup = {
    batch.info.currency match {
      case PoolInformation.CURR_ERG =>
        new PayoutGroup(ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState, batch.blocks.head.gEpoch,
          batch.blocks.head.blockheight, batch.info.poolTag)
      case _ =>
        throw new Exception("Unsupported currency found during StateGroup routing!")
    }
  }
}
