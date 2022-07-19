package plasma_test

import group_test.MockData.ergoClient
import io.getblok.subpooling_core.contracts.plasma._
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.BalanceState
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, minerConversion}
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.groups.PayoutGroup
import io.getblok.subpooling_core.states.models.CommandTypes.SETUP
import io.getblok.subpooling_core.states.models.{CommandState, CommandTypes, State}
import io.getblok.subpooling_core.states.transforms.{InsertTransform, PayoutTransform, SetupTransform, UpdateTransform}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.InputBox
import org.scalatest.funsuite.AnyFunSuite
import plasma_test.FullStateTransformationSuite._

import scala.collection.mutable.ArrayBuffer

class PayoutGroupSuite extends AnyFunSuite{
  val balanceState = new BalanceState("test/payout_group_pool_test")
  val initBlockReward = Helpers.OneErg * 55
  var stateBox: InputBox = _
  var transformer: StateTransformer = _
  var lastState: State = _
  var commandQueue: IndexedSeq[CommandState] = _
  var payoutGroup: PayoutGroup = _

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
        val initStateBox = toInput(BalanceStateContract.buildStateBox(ctx, balanceState, dummyTokenId, dummyWallet.p2pk))
        payoutGroup = new PayoutGroup(ctx, dummyWallet, mockData, initStateBox, Seq(buildUserBox(Helpers.OneErg * 500000)),
          balanceState, 0, 0, "testpool", 1000, 100)
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







