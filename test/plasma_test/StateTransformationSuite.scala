package plasma_test

import group_test.MockData.{creatorAddress, ergoClient}
import group_test.dummyProver
import io.getblok.subpooling_core.contracts.plasma._
import io.getblok.subpooling_core.global.AppParameters.{NodeWallet, PK}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, StateBalance, StateMiner}
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.models.CommandTypes.{Command, INSERT, PAYOUT, UPDATE}
import io.getblok.subpooling_core.states.models.{CommandState, PlasmaMiner, State}
import io.getblok.subpooling_core.states.transforms.{InsertTransform, PayoutTransform, UpdateTransform}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoProver, InputBox, NetworkType, OutBox, RestApiErgoClient}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import plasma_test.StateTransformationSuite._

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import io.getblok.getblok_plasma.collections.Manifest
import org.bouncycastle.util.encoders.Hex
class StateTransformationSuite extends AnyFunSuite{
  val balanceState = new BalanceState("test", 0)
  val initBlockReward = Helpers.OneErg * 55
  var stateBox: InputBox = _
  var transformer: StateTransformer = _
  var lastState: State = _

  case class TestInfo(transform: Command, txId: String, cost: Long, txSize: Long, manifest: Manifest){
    override def toString: String = {
      s"${transform}: ${txId} ->  ${cost} tx cost -> ${txSize} bytes \n" +
      s"digest: ${Hex.toHexString(manifest.digest)} | manifest: ${manifest.toString()}"
    }

  }


  val infoBuffer: ArrayBuffer[TestInfo] = ArrayBuffer()
//  testTx()
//  updateTx()
//  payoutTx()
  setup()
  insertTx(0)
  insertTx(1)
  insertTx(2)


  updateTx(0)
  updateTx(1)
  updateTx(2)

  payoutTx(0)
  payoutTx(1)
  payoutTx(2)

  printInfo
  // updateTx()
  // payoutTx()

  def setup() = {
    ergoClient.execute {
      ctx =>
        stateBox = toInput(BalanceStateContract.buildStateBox(ctx, balanceState))
        val initState = State(stateBox, balanceState, Seq(buildUserBox(Helpers.OneErg)))
        transformer = new StateTransformer(ctx, initState)
    }
  }

  def insertTx(dataSlice: Int): Unit = {
    ergoClient.execute {
      ctx =>

        val initCommandBox = toInput(InsertBalanceContract.buildBox(ctx))
        val commandState = CommandState(initCommandBox, slicedData(dataSlice), INSERT)
        val insertTransform = InsertTransform(ctx, dummyWallet, commandState)
        val result = transformer.apply(insertTransform)
        lastState = result.nextState
        logger.info(s"${result.transaction.toJson(true)}")

        infoBuffer += TestInfo(INSERT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length, result.manifest.get)
    }
  }

  def updateTx(dataSlice: Int): Unit = {
    ergoClient.execute {
      ctx =>

        val initCommandBox = toInput(UpdateBalanceContract.buildBox(ctx, Some(slicedData(dataSlice).map(_.amountAdded).sum + Helpers.MinFee * 10)))
        val commandState = CommandState(initCommandBox, slicedData(dataSlice), UPDATE)
        val updateTransform = UpdateTransform(ctx, dummyWallet, commandState)
        val result = transformer.apply(updateTransform)

        logger.info(s"${result.transaction.toJson(true)}")
        stateBox = result.nextState.box
        lastState = result.nextState
        infoBuffer += TestInfo(UPDATE, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length, result.manifest.get)
    }
  }

  def payoutTx(dataSlice: Int): Unit = {
    ergoClient.execute {
      ctx =>

        val initCommandBox = toInput(PayoutBalanceContract.buildBox(ctx, Some(Helpers.MinFee * 10)))
        val commandState = CommandState(initCommandBox,slicedData(dataSlice), PAYOUT)
        val payoutTransform = PayoutTransform(ctx, dummyWallet, commandState)
        val result = transformer.apply(payoutTransform)

        logger.info(s"${result.transaction.toJson(true)}")
        stateBox = result.nextState.box
        lastState = result.nextState
        infoBuffer += TestInfo(PAYOUT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length, result.manifest.get)
    }
  }

  def printInfo = {
    logger.info("=============== Printing Test Info ===============")
    logger.info(s"TOTAL MINERS: ${mockData.length}")
    logger.info(s"BATCH SIZE: ${NUM_MINERS}")
    for(ti <- infoBuffer){
      logger.info(ti.toString)
    }
  }

}
object StateTransformationSuite {
  val NUM_MINERS = 150
  val mockData = {
    MockAddresses.addresses.sortBy(a => BigInt(StateMiner(a).toPartialStateMiner.bytes)).zipWithIndex.map {
      ad =>
        val randomRange = {
          Helpers.OneErg * 20
        }

        val randomFloor = {
          Helpers.OneErg
        }
        PlasmaMiner(Address.create(ad._1), 0L, 0L, ((Math.random() * randomRange) + randomFloor).toLong, 0L, 0L, 0L, 0L)
    }
  }

  val slicedData = mockData.sliding(NUM_MINERS, NUM_MINERS).toSeq


  val ergoClient: ErgoClient = RestApiErgoClient.create("http://188.34.207.91:9053/", NetworkType.MAINNET, "", RestApiErgoClient.defaultMainnetExplorerUrl)
  val creatorAddress: Address = Address.create("4MQyML64GnzMxZgm")
  val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"

  def toInput(outBox: OutBox) = outBox.convertToInputWith(dummyTxId, 0)

  def dummyProver: ErgoProver = {
    ergoClient.execute{
      ctx =>
        val prover = ctx.newProverBuilder()
          .withDLogSecret(BigInt.apply(0).bigInteger)
          .build()

        return prover
    }
  }

  val dummyWallet: NodeWallet = NodeWallet(PK(creatorAddress), dummyProver)

  def logger: Logger = LoggerFactory.getLogger("StateTransformationSuite")
  def buildUserBox(value: Long): InputBox = {
    ergoClient.execute{
      ctx =>
        val inputBox = ctx.newTxBuilder().outBoxBuilder()
          .value(value)
          .contract(new ErgoTreeContract(creatorAddress.getErgoAddress.script, NetworkType.MAINNET))
          .build()
          .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)

        return inputBox
    }
  }
}




