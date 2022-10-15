package plasma_test

import io.getblok.subpooling_core.contracts.emissions.{HybridExchangeContract, HybridNormalContract}
import io.getblok.subpooling_core.contracts.plasma.{PlasmaHoldingContract, PlasmaScripts}
import io.getblok.subpooling_core.cycles.{HybridExchangeCycle, HybridNormalExchangeCycle}
import io.getblok.subpooling_core.cycles.models.CycleState
import io.getblok.subpooling_core.global.Helpers
import org.ergoplatform.appkit.{ErgoId, ErgoToken}
import org.scalatest.funsuite.AnyFunSuite
import plasma_test.HybridNormalCycleSuite.fluxToken
import plasma_test.PlasmaHybridExchangeSuite._
import plasma_utils.payments.{ExRateUtils, NFTUtils}
import play.api.test.WsTestClient

class HybridNormalCycleSuite extends AnyFunSuite {

  var exRate: Double = 0
  WsTestClient.withClient {
    client =>
      import scala.concurrent.ExecutionContext.Implicits.global
      exRate = ExRateUtils.getExRate(client)
      logger.info(s"exRate: ${exRate}")
  }
  ergoClient.execute{
    ctx =>

      val contract = HybridNormalContract.generate(ctx, creatorAddress, sigmaTrue,
        PlasmaHoldingContract.generate(ctx, dummyWallet.p2pk, dummyTokenId, PlasmaScripts.DUAL),
        fluxToken, Helpers.OneErg / 10)

      val box = toInput (HybridNormalContract.buildGenesisBox(ctx, contract, 3000L, 25000L, ctx.getHeight, dummyTokenId,
        new ErgoToken(fluxToken, (Helpers.OneErg / 10) * 100000)))


      val cycle = new HybridNormalExchangeCycle(ctx, dummyWallet, 48 * Helpers.OneErg, 3000L, 25000L,
        sigmaTrue, dummyTokenId, dummyTokenId, fluxToken, exRate, Helpers.OneErg / 10, explorerHandler)

      val result = cycle.simulateSwap

      logger.info(result.toString)
      val cycleState = CycleState(box, Seq(buildUserBox(Helpers.OneErg * 50)), Seq())
      val cycleResults = cycle.cycle(cycleState, result, false)


  }
}

object HybridNormalCycleSuite {
  val fluxToken = ErgoId.create("e8b20745ee9d18817305f32eb21015831a48f02d40980de6e849f886dca7f807")
}


