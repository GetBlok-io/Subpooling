package plasma_test

import io.getblok.subpooling_core.boxes.HybridExchangeBox
import io.getblok.subpooling_core.contracts.emissions.HybridExchangeContract
import io.getblok.subpooling_core.contracts.plasma.{PlasmaHoldingContract, PlasmaScripts}
import io.getblok.subpooling_core.cycles.HybridExchangeCycle
import io.getblok.subpooling_core.cycles.models.CycleState
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.registers.PoolFees
import org.ergoplatform.appkit.{Address, ErgoToken}
import org.scalatest.funsuite.AnyFunSuite
import plasma_test.PlasmaHybridExchangeSuite._

import scala.jdk.CollectionConverters.seqAsJavaListConverter

class HybridExchangeCycleSuite extends AnyFunSuite {

  ergoClient.execute{
    ctx =>

      val contract = HybridExchangeContract.generate(ctx, creatorAddress, sigmaTrue,
        PlasmaHoldingContract.generate(ctx, dummyWallet.p2pk, dummyTokenId, PlasmaScripts.DUAL),
        ergopadLPNFT, ergopadToken)

      val box = toInput (HybridExchangeContract.buildGenesisBox(ctx, contract, 10000L, 3000L, 10000L, dummyTokenId,
        new ErgoToken(ergopadToken, 10000000000000L)))


      val cycle = new HybridExchangeCycle(ctx, dummyWallet, 48 * Helpers.OneErg, 3000L, 10000L,
        10000L, sigmaTrue, dummyTokenId, dummyTokenId, ergopadToken, ergopadLPNFT, explorerHandler)

      val result = cycle.simulateSwap

      logger.info(result.toString)
      val cycleState = CycleState(box, Seq(buildUserBox(Helpers.OneErg * 50)), Seq())
      val cycleResults = cycle.cycle(cycleState, result, false)


  }
}


