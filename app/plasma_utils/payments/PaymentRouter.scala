package plasma_utils.payments

import actors.StateRequestHandler.PoolBox
import akka.actor.ActorRef
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.cycles.HybridExchangeCycle
import io.getblok.subpooling_core.cycles.models.Cycle
import io.getblok.subpooling_core.explorer.ExplorerHandler
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{MinerSettings, PoolInformation}
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, dualBalanceConversion}
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, SingleBalance, StateBalance}
import io.getblok.subpooling_core.states.groups.{DualGroup, PayoutGroup, StateGroup}
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
                                        poolBox: PoolBox[T], miners: Seq[PlasmaMiner], inputBoxes: Seq[InputBox],
                                         holdingBox: Option[InputBox] = None): StateGroup[_ <: StateBalance] = {
    batch.info.currency match {
      case PoolInformation.CURR_ERG =>
        new PayoutGroup(ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState.asInstanceOf[BalanceState[SingleBalance]], batch.blocks.head.gEpoch,
          batch.blocks.head.blockheight, batch.info.poolTag, batch.info.fees, Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum))
      case PoolInformation.CURR_ERG_ERGOPAD =>
        require(holdingBox.isDefined, "Holding box was not defined!")
        val template = EmissionTemplates.getErgoPadTemplate(ctx.getNetworkType)
        new DualGroup(ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState.asInstanceOf[BalanceState[DualBalance]],
          batch.blocks.head.gEpoch, batch.blocks.head.blockheight, batch.info.poolTag, holdingBox.get, template.distToken,
        "ergopad")
      case _ =>
        throw new Exception("Unsupported currency found during StateGroup routing!")
    }
  }

  def routeBalanceState(info: PoolInformation): BalanceState[_ <: StateBalance] = {
    info.currency match {
      case PoolInformation.CURR_ERG =>
        new BalanceState[SingleBalance](info.poolTag)
      case PoolInformation.CURR_ERG_ERGOPAD =>
        new BalanceState[DualBalance](info.poolTag)
    }
  }

  def routeBalanceState(script: ScriptType, poolTag: String): BalanceState[_ <: StateBalance] = {
    script match {
      case PlasmaScripts.SINGLE => new BalanceState[SingleBalance](poolTag)
      case PlasmaScripts.DUAL => new BalanceState[DualBalance](poolTag)
    }
  }

  def routePlasmaBlocks(blocks: Seq[SPoolBlock], infos: Seq[PoolInformation], routePlasma: Boolean): Seq[SPoolBlock] = {
    val plasmaPayers = Seq(PoolInformation.PAY_PLASMA_SOLO, PoolInformation.PAY_PLASMA_PPLNS)

    if(routePlasma){
      logger.info("Searching for Plasma Pools to pay")
      val plasmaInfos = infos.filter(i => plasmaPayers.contains(i.payment_type))
      val plasmaBlocks = blocks.filter(b => plasmaInfos.exists(pi => pi.poolTag == b.poolTag))
      plasmaBlocks
    }else{
      logger.info("Searching for normal Pools to pay")
      val normalInfos = infos.filter(i => !plasmaPayers.contains(i.payment_type))
      val normalBlocks = blocks.filter(b => normalInfos.exists(pi => pi.poolTag == b.poolTag))
      normalBlocks
    }
  }

  def routeMinerBalances[T <: StateBalance](balanceState: BalanceState[T], partialPlasmaMiners: Seq[PlasmaMiner],
                                            batch: BatchSelection): Seq[PlasmaMiner] = {
    batch.info.currency match {
      case PoolInformation.CURR_ERG =>
        logger.info("Routing Plasma Miners through Single Balance State")
        val singleMap = balanceState.asInstanceOf[BalanceState[SingleBalance]]
        singleMap.map.initiate()
        val balances = partialPlasmaMiners zip singleMap.map.lookUp(partialPlasmaMiners.map(_.toStateMiner.toPartialStateMiner):_*).response
        singleMap.map.dropChanges()
        val plasmaMiners = balances.map{
          b =>
            b._1.copy(
              balance = b._2.tryOp.get.map(_.balance).getOrElse(0L)
            )
        }
          .sortBy(m => BigInt(m.toStateMiner.toPartialStateMiner.bytes))
        plasmaMiners
      case PoolInformation.CURR_ERG_ERGOPAD =>
        logger.info("Routing Plasma Miners through Dual Balance State")

        val dualMap = balanceState.asInstanceOf[BalanceState[DualBalance]]
        dualMap.map.initiate()
        val balances = partialPlasmaMiners zip dualMap.map.lookUp(partialPlasmaMiners.map(_.toStateMiner.toPartialStateMiner):_*).response
        dualMap.map.dropChanges()
        val plasmaMiners = balances.map{
          b =>
            b._1.copy(
              balance = b._2.tryOp.get.map(_.balance).getOrElse(0L),
              balanceTwo = b._2.tryOp.get.map(_.balanceTwo).getOrElse(0L)
            )
        }
          .sortBy(m => BigInt(m.toStateMiner.toPartialStateMiner.bytes))
        plasmaMiners

    }
  }
}
