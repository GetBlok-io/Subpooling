package plasma_test

import io.getblok.subpooling_core.contracts.plasma.{BalanceStateContract, InsertBalanceContract, PayoutBalanceContract, ShareStateContract, UpdateBalanceContract}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, ShareState, StateBalance, StateMiner, StateScore}
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoId, ErgoProver, NetworkType, OutBox, RestApiErgoClient}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import plasma_test.BalanceStateSuite._
import scorex.crypto.authds.avltree.batch.Insert

import scala.jdk.CollectionConverters.seqAsJavaListConverter

class BalanceStateSuite extends AnyFunSuite{
  val balanceState = new BalanceState("test")
  val initBlockReward = Helpers.OneErg * 55

//  testTx()
//  updateTx()
//  payoutTx()

  val result = balanceState.map.lookUp( (mockData.take(400).map(_._1.toPartialStateMiner)):_*)
  logger.info(s"Response: ${result.response}")
  // updateTx()
  // payoutTx()
  def testTx(): Unit = {
    ergoClient.execute {
      ctx =>
        val initStateBox = toInput(BalanceStateContract.buildStateBox(ctx, balanceState, dummyTokenId, creatorAddress))
        val insertBox = InsertBalanceContract.applyContext(toInput(InsertBalanceContract.buildBox(ctx, dummyTokenId, Some(Helpers.MinFee * 10L))),
          balanceState,
          partialMockData.sortBy(m => BigInt(m._1.bytes)).map(_._1).take(NUM_MINERS))

        val nextStateBox = BalanceStateContract.buildStateBox(ctx, balanceState, dummyTokenId, creatorAddress)

        val inputBoxes = (Seq(initStateBox, insertBox)).asJava
        val uTx = ctx.newTxBuilder()
          .boxesToSpend(inputBoxes)
          .outputs(nextStateBox)
          .fee(Helpers.MinFee * 10)
          .sendChangeTo(creatorAddress.getErgoAddress)
          .build()

        val sTx = dummyProver.sign(uTx)
       // logger.info(sTx.toJson(true))
        logger.info(s"Total data: ${mockData.length} entries")
        logger.info(s"Cost: ${sTx.getCost}")


    }
  }

  def updateTx(): Unit = {
    ergoClient.execute {
      ctx =>
        val balanceChangeSum = partialMockData.sortBy(m => BigInt(m._1.bytes)).take(NUM_MINERS).map(_._2.balance).sum
        val initStateBox = toInput(BalanceStateContract.buildStateBox(ctx, balanceState, dummyTokenId, creatorAddress))
        val updateBoxResults = UpdateBalanceContract.applyContext(toInput(UpdateBalanceContract.buildBox(ctx, dummyTokenId, Some(balanceChangeSum + Helpers.MinFee * 10L))),
          balanceState,
          partialMockData.sortBy(m => BigInt(m._1.bytes)).take(NUM_MINERS))
        val updateBox = updateBoxResults._1
        val nextStateBox = BalanceStateContract.buildStateBox(ctx, balanceState,dummyTokenId, creatorAddress, Some(updateBoxResults._2 + Helpers.MinFee))

        val inputBoxes = (Seq(initStateBox, updateBox)).asJava
        val uTx = ctx.newTxBuilder()
          .boxesToSpend(inputBoxes)
          .outputs(nextStateBox)
          .fee(Helpers.MinFee * 10)
          .sendChangeTo(creatorAddress.getErgoAddress)
          .build()

        val sTx = dummyProver.sign(uTx)
        // logger.info(sTx.toJson(true))
        logger.info(s"Total data: ${mockData.length} entries")
        logger.info(s"Cost: ${sTx.getCost}")

    }
  }

  def payoutTx(): Unit = {
    ergoClient.execute {
      ctx =>
        val totalSum = mockData.sortBy(m => BigInt(m._1.toPartialStateMiner.bytes) ).map(_._2).take(NUM_MINERS).map(_.balance).sum

        val initStateBox = toInput(BalanceStateContract.buildStateBox(ctx, balanceState,dummyTokenId, creatorAddress, Some(totalSum + Helpers.MinFee)))
        val payoutBoxResults = PayoutBalanceContract.applyContext(
          toInput(PayoutBalanceContract.buildBox(ctx, dummyTokenId, Some(Helpers.MinFee * 10L))),
          balanceState,
          mockData.sortBy(m => BigInt(m._1.toPartialStateMiner.bytes)).take(NUM_MINERS).map(_._1))
        val payoutBox = payoutBoxResults._1
        val totalRemoved = payoutBoxResults._2.map(_._2.balance).sum
        logger.info(s"Total removed: ${totalRemoved}")
        logger.info(s"Total sum: ${totalSum}")
        val feeBox = toInput(ShareStateContract.buildFeeBox(ctx, (Helpers.MinFee * 10), creatorAddress.toErgoContract))

        val nextStateBox = BalanceStateContract.buildStateBox(ctx, balanceState, dummyTokenId, creatorAddress, Some(Helpers.MinFee))
        val payoutBoxes = BalanceStateContract.buildPaymentBoxes(ctx, payoutBoxResults._2)
        val inputBoxes = (Seq(initStateBox, payoutBox)).asJava
        val outputBoxes = Seq(nextStateBox) ++ payoutBoxes
        val uTx = ctx.newTxBuilder()
          .boxesToSpend(inputBoxes)
          .outputs(outputBoxes:_*)
          .fee(Helpers.MinFee * 10)
          .sendChangeTo(creatorAddress.getErgoAddress)
          .build()

        val sTx = dummyProver.sign(uTx)
        // logger.info(sTx.toJson(true))
        logger.info(s"Total data: ${mockData.length} entries")
        logger.info(s"Cost: ${sTx.getCost}")


    }
  }

//  def sendTx(): Unit = {
//    ergoClient.execute {
//      ctx =>
//        val initStateBox = ShareStateContract.buildStateBox(ctx, shareState, totalScore)
//        val initRewardBox = ShareStateContract.buildRewardBox(ctx, initBlockReward, initBlockReward, creatorAddress.toErgoContract)
//        val feeBox = ShareStateContract.buildFeeBox(ctx, Helpers.MinFee * 1000, creatorAddress.toErgoContract)
//
//        val initInputs = ctx.getBoxesById("25748e7479998e1e108258c30a164e3989a6f27651f403f36f1fda5061b4dc0b")
//        val initOutputs = Seq(initStateBox, initRewardBox, feeBox)
//        val initUTx = ctx.newTxBuilder()
//          .boxesToSpend(initInputs.toSeq.asJava)
//          .outputs(initOutputs:_*)
//          .fee(Helpers.MinFee * 10)
//          .sendChangeTo(creatorAddress.getErgoAddress)
//          .build()
//
//        val initSTx = dummyProver.sign(initUTx)
//
//        logger.info(s"Initial tx json: ${initSTx.toJson(true)}")
//        val initTxId = initSTx.getId.replace("\"", "")
//
//        val inputBoxes = initOutputs.slice(1, initOutputs.length).zipWithIndex.map(o => o._1.convertToInputWith(initTxId, (o._2 + 1).toShort))
//        val extendedStateBox = ShareStateContract.applyContextVars(initOutputs.head.convertToInputWith(initTxId, 0.toShort), shareState,
//          partialMockData.sortBy(m => BigInt(m._1.bytes)).take(NUM_MINERS))
//
//        val boxesToSpend = Seq(extendedStateBox) ++ inputBoxes
//
//        val nextStateBox = ShareStateContract.buildStateBox(ctx, shareState, totalScore)
//
//        val paymentBoxes = ShareStateContract.buildPaymentBoxes(ctx, mockData.sortBy(m => BigInt(m._1.toPartialStateMiner.bytes)).take(NUM_MINERS), initBlockReward, totalScore)
//        val nextRewardBox = ShareStateContract.buildRewardBox(ctx, initBlockReward - (paymentBoxes.map(_.getValue).sum), initBlockReward, creatorAddress.toErgoContract)
//        require(paymentBoxes.forall(o => o.getValue > Helpers.MinFee))
//        val uTx = ctx.newTxBuilder()
//          .boxesToSpend(boxesToSpend.asJava)
//          .outputs((Seq(nextStateBox, nextRewardBox) ++ paymentBoxes): _*)
//          .fee(Helpers.MinFee * 1000)
//          .sendChangeTo(creatorAddress.getErgoAddress)
//          .build()
//
//        val sTx = dummyProver.sign(uTx)
//        logger.info(sTx.toJson(true))
//        logger.info(s"Total data: ${mockData.length} entries")
//        logger.info(s"Cost: ${sTx.getCost}")
//      ctx.sendTransaction(initSTx)
//        logger.info(initSTx.getId)
//        Thread.sleep(3000)
//      ctx.sendTransaction(sTx)
//        logger.info(sTx.getId)
//    }
//  }

  ///====NO PROOF COMPRESSION====
    // 2200 Miner Pool
    // Airdrop with 90 miners according to random share numbers: 69b92cd36fee7f07bf53d9693a3ab56fdb43b3554b15bd1babae1d727a0201ed

    // 2239 Miner Pool
    // Paid 90 miners with random share numbers: b16633ecd76e4dc2c104e95656839f475091bb5fb39ff90bb99ff782c98fbeab
    // 204876 transaction cost (33% less than cost of single transaction on old contracts, which paid out 10 miners per transaction)
    // ~18000 byte proof size

  //====PROOF COMPRESSION + CONTEXT VARS====
  // 2239 Miner Pool
  // Paid out 300 miners with random share numbers: 4dc2502e9b0ce5de8c51a34cb4149b0bd2a243b23faef8ca3ddc950d7d35f61f
  // 208094 transaction cost (Still roughly 33% less cost for transaction)
  // 14854 byte proof size
}

object BalanceStateSuite {
  val NUM_MINERS = 198
  val mockData = {
    MockAddresses.addresses.sortBy(a => BigInt(StateMiner(a).toPartialStateMiner.bytes)).zipWithIndex.map {
      ad =>
        val randomRange = {
          Helpers.OneErg * 20
        }

        val randomFloor = {
          Helpers.OneErg
        }
        StateMiner(ad._1) -> StateBalance(((Math.random() * randomRange) + randomFloor).toLong)
    }
  }

  val partialMockData = mockData.map(m => m._1.toPartialStateMiner -> m._2)
  val ergoClient: ErgoClient = RestApiErgoClient.create("http://188.34.207.91:9053/", NetworkType.MAINNET, "", RestApiErgoClient.defaultMainnetExplorerUrl)
  val creatorAddress: Address = Address.create("4MQyML64GnzMxZgm")
  val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"
  val dummyToken = "f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819"
  val dummyTokenId = ErgoId.create(dummyToken)
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

  def logger: Logger = LoggerFactory.getLogger("PlasmaTesting")

}


