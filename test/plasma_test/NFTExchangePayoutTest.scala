package plasma_test

import io.getblok.subpooling_core.contracts.emissions.NFTExchangeContract
import io.getblok.subpooling_core.contracts.plasma.{BalanceStateContract, PlasmaHoldingContract, PlasmaScripts}
import io.getblok.subpooling_core.cycles.NFTExchangeCycle
import io.getblok.subpooling_core.cycles.models.CycleState
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolPlacement
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, minerConversion}
import io.getblok.subpooling_core.plasma.{BalanceState, SingleBalance}
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.groups.TokenPayoutGroup
import io.getblok.subpooling_core.states.models.{CommandState, PlasmaMiner, TokenState}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{Address, ErgoToken, InputBox}
import org.scalatest.funsuite.AnyFunSuite
import plasma_test.FullStateTransformationSuite.{buildHoldingBox, dummyTokenId, dummyWallet, mockData}
import plasma_test.NFTExchangeCycleSuite.{netaLPNFT, netaToken, nftHolders, placements}
import plasma_test.PlasmaHybridExchangeSuite.{buildUserBox, creatorAddress, ergoClient, explorerHandler, logger, sigmaTrue, toInput}

class NFTExchangePayoutTest extends AnyFunSuite{
  val balanceState = new BalanceState[SingleBalance]("test/token_payout_group_pool_test")
  val initBlockReward = Helpers.OneErg * 55
  var stateBox: InputBox = _
  var transformer: StateTransformer[SingleBalance] = _
  var lastState: TokenState = _
  var commandQueue: IndexedSeq[CommandState] = _
  var payoutGroup: TokenPayoutGroup = _
  val (holdingBox, nextPlacements) = ergoClient.execute {
    ctx =>

      for (holder <- nftHolders) logger.info(s"Holder ${holder.address} with count ${holder.count}")

      val contract = NFTExchangeContract.generate(ctx, creatorAddress, sigmaTrue,
        PlasmaHoldingContract.generate(ctx, dummyWallet.p2pk, dummyTokenId, PlasmaScripts.SINGLE_TOKEN),
        netaLPNFT, netaToken)

      val box = toInput(NFTExchangeContract.buildGenesisBox(ctx, contract, 10000L, 3000L, dummyTokenId,
        new ErgoToken(netaToken, 10000000000000L)))


      val cycle = new NFTExchangeCycle(ctx, dummyWallet, 48 * Helpers.OneErg, 3000L, nftHolders,
        10000L, sigmaTrue, dummyTokenId, dummyTokenId, netaToken, netaLPNFT, explorerHandler)

      val result = cycle.simulateSwap

      logger.info(result.toString)
      val cycleState = CycleState(box, Seq(buildUserBox(Helpers.OneErg * 50)), placements)
      val results = cycle.cycle(cycleState, result, false)
      logger.info(s"Total bonus added ${results.nextCycleBox.getRegisters.get(2).getValue}")
      (results.nextHoldingBox, cycle.morphPlacementValues(placements, results.emissionResults))
  }

  logger.info(s"Total amount in holding ${holdingBox.getTokens.get(0).getValue}")

      //  testTx()
  //  updateTx()
  //  payoutTx()
  setup(holdingBox, nextPlacements)
  setupTx()
  executeCommands()

  logger.info(s"initDigest: ${Hex.toHexString(payoutGroup.transformer.initDigest)}")
  logger.info(s"digest: ${payoutGroup.currentState.balanceState.map.toString()}")
  logger.info(s"currentDigest: ${balanceState.map.toString()}")
  logger.info(s"manifestDigest: ${balanceState.map.toPlasmaMap.getManifest(255).digestString}")

  logger.info(s"transform digests:")

  // printMembers()
  // updateTx()
  // payoutTx()

  def setup(holdingBox: InputBox, nextPlacers: Seq[PoolPlacement]) = {
    ergoClient.execute {
      ctx =>
        val initStateBox = toInput(BalanceStateContract.buildBox(ctx, balanceState, dummyTokenId, dummyWallet.p2pk, PlasmaScripts.SINGLE_TOKEN))
        val plasma = nextPlacers.map(toPlasma)
        payoutGroup = new TokenPayoutGroup(ctx, dummyWallet, plasma, initStateBox, Seq(),
          balanceState, 0, 0, "testpool", holdingBox , netaToken, "NETA")
    }
  }

  def setupTx() = {
    payoutGroup.setup()
  }

  def executeCommands() = {
    payoutGroup.applyTransformations()
  }

  def printMembers(): Unit = {
    payoutGroup.getMembers.foreach(m => logger.info(s"${m}"))
  }

  def toPlasma(p: PoolPlacement) = {
    PlasmaMiner(Address.create(p.miner), p.score, 0L, p.amount, p.minpay, 0, 0L, 0L)
  }

}
