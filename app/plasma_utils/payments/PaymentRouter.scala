package plasma_utils.payments

import actors.StateRequestHandler.PoolBox
import akka.actor.ActorRef
import io.getblok.subpooling_core.cycles.HybridExchangeCycle
import io.getblok.subpooling_core.cycles.models.Cycle
import io.getblok.subpooling_core.explorer.ExplorerHandler
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{MinerSettings, PoolInformation}
import io.getblok.subpooling_core.plasma.{BalanceState, SingleBalance, StateBalance}
import io.getblok.subpooling_core.states.groups.{PayoutGroup, StateGroup}
import io.getblok.subpooling_core.states.models.{CommandBatch, PlasmaMiner}
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import persistence.shares.ShareCollector
import scorex.crypto.authds.ADDigest
import utils.ConcurrentBoxLoader.BatchSelection
import utils.EmissionTemplates

object PaymentRouter {
  private val logger: Logger = LoggerFactory.getLogger("PaymentRouter")
  def routeProcessor(info: PoolInformation, settings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection, reward: Long,
                     emReq: ActorRef): PaymentProcessor = {
    info.currency match {
      case PoolInformation.CURR_ERG =>
        StandardProcessor(settings, collector, batch, reward, info.fees)
      case PoolInformation.CURR_ERG_ERGOPAD =>
        HybridExchangeProcessor(settings, collector, batch, reward, info.fees, emReq)
      case _ =>
        throw new Exception("Unsupported currency found during processor routing!")
    }
  }

  def routeCycle(ctx: BlockchainContext, wallet: NodeWallet, reward: Long,
                 batchSelection: BatchSelection, explorerHandler: ExplorerHandler): Cycle = {
    batchSelection.info.currency match {
      case PoolInformation.CURR_ERG_ERGOPAD =>
        val template = EmissionTemplates.getErgoPadTemplate(ctx.getNetworkType)

        new HybridExchangeCycle(
          ctx, wallet, reward, batchSelection.info.fees,
          template.proportion, template.percent, template.swapAddress,
          ErgoId.create(batchSelection.info.poolTag), ErgoId.create(batchSelection.info.emissions_id),
          template.distToken, template.lpNFT, explorerHandler
        )
      case _ =>
        throw new Exception("Unsupported currency found during cycle routing!")
    }
  }

  def routeStateGroup[T <: StateBalance](ctx: BlockchainContext, wallet: NodeWallet, batch: BatchSelection,
                                        poolBox: PoolBox[T], miners: Seq[PlasmaMiner], inputBoxes: Seq[InputBox]): StateGroup[_ <: StateBalance] = {
    batch.info.currency match {
      case PoolInformation.CURR_ERG =>
        new PayoutGroup(ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState.asInstanceOf[BalanceState[SingleBalance]], batch.blocks.head.gEpoch,
          batch.blocks.head.blockheight, batch.info.poolTag, batch.info.fees, Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum))
      case _ =>
        throw new Exception("Unsupported currency found during StateGroup routing!")
    }
  }

  def routePlasmaBlocks(blocks: Seq[SPoolBlock], infos: Seq[PoolInformation], routePlasma: Boolean): Seq[SPoolBlock] = {
    val plasmaPayers = Seq(PoolInformation.PAY_PLASMA_SOLO, PoolInformation.PAY_PLASMA_PPLNS)

    if(routePlasma){
      val plasmaInfos = infos.filter(i => plasmaPayers.contains(i.payment_type))
      val plasmaBlocks = blocks.filter(b => plasmaInfos.exists(pi => pi.poolTag == b.poolTag))
      plasmaBlocks
    }else{
      val normalInfos = infos.filter(i => !plasmaPayers.contains(i.payment_type))
      val normalBlocks = blocks.filter(b => normalInfos.exists(pi => pi.poolTag == b.poolTag))
      normalBlocks
    }
  }
}
