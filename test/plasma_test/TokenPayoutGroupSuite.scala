package plasma_test

import group_test.MockData.ergoClient
import io.getblok.subpooling_core.contracts.plasma._
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, dualBalanceConversion, minerConversion}
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, SingleBalance}
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.groups.{DualGroup, TokenPayoutGroup}
import io.getblok.subpooling_core.states.models.{CommandState, DualState, TokenState}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.InputBox
import org.scalatest.funsuite.AnyFunSuite
import plasma_test.DualPayoutGroupSuite.{ergopadToken, netaToken}
import plasma_test.FullStateTransformationSuite._

class TokenPayoutGroupSuite extends AnyFunSuite{
  val balanceState = new BalanceState[SingleBalance]("test/token_payout_group_pool_test")
  val initBlockReward = Helpers.OneErg * 55
  var stateBox: InputBox = _
  var transformer: StateTransformer[SingleBalance] = _
  var lastState: TokenState = _
  var commandQueue: IndexedSeq[CommandState] = _
  var payoutGroup: TokenPayoutGroup = _

//  testTx()
//  updateTx()
//  payoutTx()
  setup()
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

  def setup() = {
    ergoClient.execute {
      ctx =>
        val initStateBox = toInput(BalanceStateContract.buildBox(ctx, balanceState, dummyTokenId, dummyWallet.p2pk, PlasmaScripts.SINGLE_TOKEN))
        val holdingBox = buildHoldingBox(Helpers.OneErg * 500000, Helpers.OneErg * 500000, netaToken)
        payoutGroup = new TokenPayoutGroup(ctx, dummyWallet, mockData, initStateBox, Seq(),
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



}







