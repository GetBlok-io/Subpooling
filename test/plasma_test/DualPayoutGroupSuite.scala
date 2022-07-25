package plasma_test

import group_test.MockData.ergoClient
import io.getblok.subpooling_core.contracts.plasma._
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, dualBalanceConversion, minerConversion}
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, SingleBalance}
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.groups.{DualGroup, PayoutGroup}
import io.getblok.subpooling_core.states.models.{CommandState, DualState, SingleState}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{ErgoId, InputBox}
import org.scalatest.funsuite.AnyFunSuite
import plasma_test.DualPayoutGroupSuite.ergopadToken
import plasma_test.FullStateTransformationSuite._

class DualPayoutGroupSuite extends AnyFunSuite{
  val balanceState = new BalanceState[DualBalance]("test/dual_payout_group_pool_test")
  val initBlockReward = Helpers.OneErg * 55
  var stateBox: InputBox = _
  var transformer: StateTransformer[DualBalance] = _
  var lastState: DualState = _
  var commandQueue: IndexedSeq[CommandState] = _
  var payoutGroup: DualGroup = _

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
        val initStateBox = toInput(BalanceStateContract.buildBox(ctx, balanceState, dummyTokenId, dummyWallet.p2pk, PlasmaScripts.DUAL))
        val holdingBox = buildHoldingBox(Helpers.OneErg * 500000, Helpers.OneErg * 500000, ergopadToken)
        payoutGroup = new DualGroup(ctx, dummyWallet, mockData, initStateBox, Seq(),
          balanceState, 0, 0, "testpool", holdingBox , ergopadToken, "ergopad")
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
object DualPayoutGroupSuite {
  val ergopadToken = ErgoId.create("d71693c49a84fbbecd4908c94813b46514b18b67a99952dc1e6e4791556de413")
  val ergopadLPNFT = ErgoId.create("d7868533f26db1b1728c1f85c2326a3c0327b57ddab14e41a2b77a5d4c20f4b2")
}






