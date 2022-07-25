package plasma_utils.payments

import actors.StateRequestHandler.PoolBox
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{MinerSettings, PoolInformation}
import io.getblok.subpooling_core.states.groups.{PayoutGroup, StateGroup}
import io.getblok.subpooling_core.states.models.{CommandBatch, PlasmaMiner}
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{BlockchainContext, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import persistence.shares.ShareCollector
import scorex.crypto.authds.ADDigest
import utils.ConcurrentBoxLoader.BatchSelection

object PaymentRouter {
  private val logger: Logger = LoggerFactory.getLogger("PaymentRouter")
  def routeProcessor(info: PoolInformation, settings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection, reward: Long): PaymentProcessor = {
    info.currency match {
      case PoolInformation.CURR_ERG =>
        StandardProcessor(settings, collector, batch, reward, info.fees)
      case _ =>
        throw new Exception("Unsupported currency found during processor routing!")
    }
  }

  def routeStateGroup(ctx: BlockchainContext, wallet: NodeWallet, batch: BatchSelection,
                      poolBox: PoolBox, miners: Seq[PlasmaMiner], inputBoxes: Seq[InputBox]): StateGroup = {
    batch.info.currency match {
      case PoolInformation.CURR_ERG =>

        new PayoutGroup(ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState, batch.blocks.head.gEpoch,
          batch.blocks.head.blockheight, batch.info.poolTag, batch.info.fees, Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum)
        )
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
