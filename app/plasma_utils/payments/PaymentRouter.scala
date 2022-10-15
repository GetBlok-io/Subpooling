package plasma_utils.payments

import actors.StateRequestHandler.PoolBox
import akka.actor.ActorRef
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.cycles.{HybridExchangeCycle, HybridNormalExchangeCycle, NFTExchangeCycle}
import io.getblok.subpooling_core.cycles.models.{Cycle, NFTHolder}
import io.getblok.subpooling_core.explorer.ExplorerHandler
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{MinerSettings, PoolInformation}
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, dualBalanceConversion}
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, SingleBalance, StateBalance}
import io.getblok.subpooling_core.states.groups.{DualGroup, PayoutGroup, StateGroup, TokenPayoutGroup}
import io.getblok.subpooling_core.states.models.{CommandBatch, PlasmaMiner}
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import persistence.shares.ShareCollector
import play.api.libs.ws.WSClient
import scorex.crypto.authds.ADDigest
import utils.ConcurrentBoxLoader.BatchSelection
import utils.EmissionTemplates

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Try}


object PaymentRouter {
  private val logger: Logger = LoggerFactory.getLogger("PaymentRouter")
  def routeProcessor(info: PoolInformation, settings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection, reward: Long,
                     emReq: ActorRef): PaymentProcessor = {
    info.currency match {
      case PoolInformation.CURR_ERG =>
        StandardProcessor(settings, collector, batch, reward, info.fees)
      case PoolInformation.CURR_ERG_ERGOPAD =>
        HybridExchangeProcessor(settings, collector, batch, reward, info.fees, emReq)
      case PoolInformation.CURR_ERG_FLUX =>
        HybridNormalExchangeProcessor(settings, collector, batch, reward, info.fees, emReq)
      case PoolInformation.CURR_NETA =>
        NFTExchangeProcessor(settings, collector, batch, reward, info.fees, emReq)
      case _ =>
        throw new Exception("Unsupported currency found during processor routing!")
    }
  }

  def routeCycle(ctx: BlockchainContext, wallet: NodeWallet, reward: Long,
                 batchSelection: BatchSelection, explorerHandler: ExplorerHandler,
                 optWs: Option[WSClient] = None, optEc: Option[ExecutionContext] = None): Cycle = {
    batchSelection.info.currency match {
      case PoolInformation.CURR_ERG_ERGOPAD =>
        val template = EmissionTemplates.getErgoPadTemplate(ctx.getNetworkType)

        new HybridExchangeCycle(
          ctx, wallet, reward, batchSelection.info.fees,
          template.proportion, template.percent, template.swapAddress,
          ErgoId.create(batchSelection.info.poolTag), ErgoId.create(batchSelection.info.emissions_id),
          template.distToken, template.lpNFT, explorerHandler
        )
      case PoolInformation.CURR_ERG_FLUX =>
        val template = EmissionTemplates.getFluxTemplate(ctx.getNetworkType)
        logger.info("Creating Flux cycle")

        logger.info("Now grabbing Erg->Flux exchange rate")
        val exRate = ExRateUtils.getExRate(optWs.get)(optEc.get)
        logger.info(s"Current Erg->Flux: ${exRate}")
        new HybridNormalExchangeCycle(
          ctx, wallet, reward, batchSelection.info.fees,
          template.proportion, template.swapAddress,
          ErgoId.create(batchSelection.info.poolTag), ErgoId.create(batchSelection.info.emissions_id),
          template.distToken, exRate, template.decimals, explorerHandler
        )
      case PoolInformation.CURR_NETA =>
        logger.info("Creating NETA NFT cycle")
        val template = EmissionTemplates.getNETATemplate(ctx.getNetworkType)
        logger.info("Now grabbing neta nft holders")
        val nftHolders = Try{
          NFTUtils.getAnetaNFTHolders(optWs.get)(optEc.get)
        }.recoverWith{
          case e: Throwable =>
            logger.error(s"Failure while grabbing nfts: ${e.toString}")
            Failure(e)
        }.get
        logger.info(s"Number of nft holders: ${nftHolders.length}")
        Try{
          new NFTExchangeCycle(
            ctx, wallet, reward, batchSelection.info.fees, nftHolders,
            template.initPercent, template.swapAddress, ErgoId.create(batchSelection.info.poolTag),
            ErgoId.create(batchSelection.info.emissions_id), template.distToken,
            template.lpNFT, explorerHandler
          )
        }.recoverWith{
          case e: Throwable =>
            logger.error(s"Failure while creating cycle ${e.toString}")
            Failure(e)
        }.get
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
        new DualGroup(
          ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState.asInstanceOf[BalanceState[DualBalance]],
          batch.blocks.head.gEpoch, batch.blocks.head.blockheight, batch.info.poolTag, holdingBox.get, template.distToken,
        "ergopad"
        )
      case PoolInformation.CURR_NETA =>
        require(holdingBox.isDefined, "Holding box was not defined!")
        val template = EmissionTemplates.getNETATemplate(ctx.getNetworkType)
        new TokenPayoutGroup(
          ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState.asInstanceOf[BalanceState[SingleBalance]],
          batch.blocks.head.gEpoch, batch.blocks.head.blockheight, batch.info.poolTag, holdingBox.get, template.distToken, "NETA"
        )
      case PoolInformation.CURR_ERG_FLUX =>
        require(holdingBox.isDefined, "Holding box was not defined!")
        val template = EmissionTemplates.getFluxTemplate(ctx.getNetworkType)
        new DualGroup(
          ctx, wallet, miners, poolBox.box, inputBoxes, poolBox.balanceState.asInstanceOf[BalanceState[DualBalance]],
          batch.blocks.head.gEpoch, batch.blocks.head.blockheight, batch.info.poolTag, holdingBox.get, template.distToken,
          "Flux"
        )
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
      case PoolInformation.CURR_NETA =>
        new BalanceState[SingleBalance](info.poolTag)
      case PoolInformation.CURR_ERG_FLUX =>
        new BalanceState[DualBalance](info.poolTag)
    }
  }

  def routeBalanceState(script: ScriptType, poolTag: String): BalanceState[_ <: StateBalance] = {
    script match {
      case PlasmaScripts.SINGLE => new BalanceState[SingleBalance](poolTag)
      case PlasmaScripts.DUAL => new BalanceState[DualBalance](poolTag)
      case PlasmaScripts.SINGLE_TOKEN => new BalanceState[SingleBalance](poolTag)
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
          .filter(pm => pm.amountAdded > 0 || pm.balance > 0) // Ensure all miners made a contribution
      case PoolInformation.CURR_NETA =>
        logger.info("Routing Plasma Miners through Single Token Balance State")

        val singleMap = balanceState.asInstanceOf[BalanceState[SingleBalance]]
        singleMap.map.initiate()
        val balances = partialPlasmaMiners zip singleMap.map.lookUp(partialPlasmaMiners.map(_.toStateMiner.toPartialStateMiner):_*).response
        singleMap.map.dropChanges()
        val plasmaMiners = balances.map{
          b =>
            b._1.copy(
              balance = b._2.tryOp.get.map(_.balance).getOrElse(0L),
            )
        }
          .sortBy(m => BigInt(m.toStateMiner.toPartialStateMiner.bytes))
        plasmaMiners
          .filter(pm => pm.amountAdded > 0 || pm.balance > 0) // Ensure all miners made a contribution
      case PoolInformation.CURR_ERG_FLUX =>
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
          .filter(pm => pm.amountAdded > 0 || pm.balance > 0) // Ensure all miners made a contribution
    }
  }
}
