package plasma_test

import group_test.MockData.ergoClient
import io.getblok.subpooling_core.contracts.plasma._
import io.getblok.subpooling_core.global.AppParameters.{NodeWallet, PK}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, StateMiner}
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.models.CommandTypes.SETUP
import io.getblok.subpooling_core.states.models.{CommandState, CommandTypes, PlasmaMiner, State}
import io.getblok.subpooling_core.states.transforms.{InsertTransform, PayoutTransform, SetupTransform, UpdateTransform}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoProver, InputBox, NetworkType, OutBox, RestApiErgoClient}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import plasma_test.FullStateTransformationSuite._

import scala.collection.mutable.ArrayBuffer

class FullStateTransformationSuite extends AnyFunSuite{
  val balanceState = new BalanceState("state_transform_suite")
  val initBlockReward = Helpers.OneErg * 55
  var stateBox: InputBox = _
  var transformer: StateTransformer = _
  var lastState: State = _
  var commandQueue: IndexedSeq[CommandState] = _
  case class TestInfo(transform: String, txId: String, cost: Long, txSize: Long){
    override def toString: String = s"${transform}: ${txId} ->  ${cost} tx cost -> ${txSize} bytes"
  }

  val PAYOUT = "PAYOUT"
  val UPDATE = "UPDATE"
  val INSERT = "INSERT"

  val infoBuffer: ArrayBuffer[TestInfo] = ArrayBuffer()
//  testTx()
//  updateTx()
//  payoutTx()
  setup()
  setupTx()
  executeCommands()
  printInfo
  // updateTx()
  // payoutTx()

  def setup() = {
    ergoClient.execute {
      ctx =>
        logger.info(mockData.map(_.amountAdded).sum + " total value")
        stateBox = toInput(BalanceStateContract.buildStateBox(ctx, balanceState))
        val initState = State(stateBox, balanceState, Seq(buildUserBox(Helpers.OneErg * 1000000L)))
        transformer = new StateTransformer(ctx, initState)
    }
  }

  def setupTx() = {
    ergoClient.execute {
      ctx =>
        val dummyCommandState = CommandState(stateBox, mockData, SETUP, -1)
        val setupTransform = SetupTransform(ctx, dummyWallet, dummyCommandState, NUM_MINERS)
        val result = transformer.apply(setupTransform)
        commandQueue = setupTransform.commandQueue
    }
  }

  def executeCommands() = {
    commandQueue.foreach{
      cmdState =>
        cmdState.commandType match {
          case CommandTypes.INSERT =>
            insertTx(cmdState)
          case CommandTypes.UPDATE =>
            updateTx(cmdState)
          case CommandTypes.PAYOUT =>
            payoutTx(cmdState)
        }
    }
  }

  def insertTx(commandState: CommandState): Unit = {
    ergoClient.execute {
      ctx =>

        val insertTransform = InsertTransform(ctx, dummyWallet, commandState)
        val result = transformer.apply(insertTransform)
        lastState = result.nextState
        logger.info(s"${result.transaction.toJson(true)}")

        infoBuffer += TestInfo(INSERT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
    }
  }

  def updateTx(commandState: CommandState): Unit = {
    ergoClient.execute {
      ctx =>

        val updateTransform = UpdateTransform(ctx, dummyWallet, commandState)
        val result = transformer.apply(updateTransform)

        logger.info(s"${result.transaction.toJson(true)}")
        stateBox = result.nextState.box
        lastState = result.nextState
        infoBuffer += TestInfo(UPDATE, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
    }
  }

  def payoutTx(commandState: CommandState): Unit = {
    ergoClient.execute {
      ctx =>

        val payoutTransform = PayoutTransform(ctx, dummyWallet, commandState)
        val result = transformer.apply(payoutTransform)

        logger.info(s"${result.transaction.toJson(true)}")
        stateBox = result.nextState.box
        lastState = result.nextState
        infoBuffer += TestInfo(PAYOUT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
    }
  }

  def printInfo = {
    logger.info("=============== Printing Test Info ===============")
    logger.info(s"TOTAL MINERS: ${mockData.length}")
    logger.info(s"BATCH SIZE: ${NUM_MINERS}")
    logger.info(s"AMOUNT ADDED: ${mockData.map(_.amountAdded).sum}")
    logger.info(s"AMOUNT REMAINING: ${lastState.box.getValue}")
    for(ti <- infoBuffer){
      logger.info(ti.toString)
    }
  }

}

object FullStateTransformationSuite {
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

        val randMinPayRange = {
          Helpers.OneErg * 20
        }

        val randMinPayFloor = {
          Helpers.OneErg
        }

        val randomMinPay = ((Math.random() * randMinPayRange) + randMinPayFloor).toLong

        PlasmaMiner(Address.create(ad._1), 0L, 0L, ((Math.random() * randomRange) + randomFloor).toLong, randomMinPay, 0, 0, 0)
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





