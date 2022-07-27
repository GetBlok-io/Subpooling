package io.getblok.subpooling_core
package cycles

import io.getblok.subpooling_core.contracts.emissions.HybridExchangeContract
import io.getblok.subpooling_core.contracts.emissions.HybridExchangeContract.simulateSwap
import io.getblok.subpooling_core.cycles.models.{CycleState, EmissionResults}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolPlacement
import io.getblok.subpooling_core.registers.PoolFees
import org.ergoplatform.appkit.impl.NodeAndExplorerDataSourceImpl
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, ErgoValue, InputBox}

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

class HybridExchangeCycle(ctx: BlockchainContext, wallet: NodeWallet, cycleState: Option[CycleState], reward: Long, fee: Long,
                          proportion: Long, percent: Long, poolOp: Address, distToken: ErgoId, lpNFT: ErgoId) {

  def getLPBox: InputBox = {
    val dataSource = ctx.getDataSource
    val lpAddress = Address.create(HybridExchangeContract.getSwapAddress(ctx.getNetworkType))
    val lpBoxes = dataSource.getUnconfirmedUnspentBoxesFor(
      lpAddress,
      0,
      100
    ).asScala.toSeq

    val mostRecentLPBoxes = lpBoxes.sortBy(_.getCreationHeight).reverse
    val unconfirmedLPBox = mostRecentLPBoxes.find(i => i.getTokens.get(0).getId == lpNFT)

    unconfirmedLPBox.getOrElse{
      val confirmedLPBoxes = dataSource.getUnspentBoxesFor(lpAddress, 0, 100)
      confirmedLPBoxes.asScala.find(_.getId == lpNFT).getOrElse(throw new LPBoxNotFoundException(lpNFT))
    }
  }

  def simulateSwap: EmissionResults = {
    val lpBox = getLPBox
    val ergAfterFees = reward - ((reward * fee) / PoolFees.POOL_FEE_CONST)
    val amountToSwap = (ergAfterFees * proportion) / PoolFees.POOL_FEE_CONST
    val amountInErg  = ergAfterFees - amountToSwap
    val outputTokens = HybridExchangeContract.simulateSwap(amountToSwap, 0.01, lpBox.getValue.toLong,
      lpBox.getTokens.get(2).getValue.toLong, lpBox.getRegisters.get(0).asInstanceOf[ErgoValue[Int]].getValue.toLong,
      1000)

    val rate = HybridExchangeContract.simulateSwap(Helpers.OneErg, 0.01, lpBox.getValue.toLong,
      lpBox.getTokens.get(2).getValue.toLong, lpBox.getRegisters.get(0).asInstanceOf[ErgoValue[Int]].getValue.toLong,
      1000).toDouble / 100 // FOR ERGOPAD, CHANGE LATER

    val adjustedOutput = outputTokens + ((outputTokens * percent) / PoolFees.POOL_FEE_CONST)

    val emissionRate = adjustedOutput.toDouble / (amountToSwap.toDouble / Helpers.OneErg)

    EmissionResults(adjustedOutput, amountToSwap, amountInErg, rate, Some(emissionRate))
  }

  def morphPlacements(placements: Seq[PoolPlacement], emissionResults: EmissionResults) = {
    ???
  }

  def cycle(cycleState: CycleState, emissionResults: EmissionResults, placements: Seq[PoolPlacement], sendTxs: Boolean = true) = {
    val reArranged = CycleHelper.reArrange(ctx, wallet, cycleState.inputBoxes, reward)
    val rewardInput = reArranged._1


  }




}
