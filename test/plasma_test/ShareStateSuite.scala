package plasma_test

import io.getblok.subpooling_core.contracts.plasma.ShareStateContract
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{ShareState, StateMiner, StateScore}
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoProver, NetworkType, OutBox, RestApiErgoClient}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import plasma_test.ShareStateSuite.{creatorAddress, dummyProver, ergoClient, logger, mockData, partialMockData, toInput}

import scala.jdk.CollectionConverters.seqAsJavaListConverter

class ShareStateSuite extends AnyFunSuite{
  val shareState = new ShareState("test", 0)
  val initBlockReward = Helpers.OneErg * 48
  val totalScore = mockData.map(_._2.score).sum.toInt
  shareState.loadState(partialMockData)
  ergoClient.execute{
    ctx =>
      val initStateBox = toInput(ShareStateContract.buildStateBox(ctx, shareState, totalScore))
      val initRewardBox = toInput(ShareStateContract.buildRewardBox(ctx, initBlockReward, initBlockReward, creatorAddress.toErgoContract))
      val initDataBoxes = ShareStateContract.buildDataBoxes(ctx, shareState, partialMockData.slice(100, partialMockData.length).take(100), creatorAddress.toErgoContract).map(toInput)
      logger.info(s"Number of data boxes: ${initDataBoxes.length}")
      val nextStateBox = ShareStateContract.buildStateBox(ctx, shareState, totalScore)

      val paymentBoxes  = ShareStateContract.buildPaymentBoxes(ctx, mockData.slice(100, partialMockData.length).take(100), initBlockReward, totalScore)
      val nextRewardBox = ShareStateContract.buildRewardBox(ctx, initBlockReward - (paymentBoxes.map(_.getValue).sum), initBlockReward, creatorAddress.toErgoContract)
      val inputBoxes = (Seq(initStateBox, initRewardBox) ++ initDataBoxes).asJava
      val uTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs((Seq(nextStateBox, nextRewardBox) ++ paymentBoxes):_*)
        .fee(Helpers.MinFee * initDataBoxes.length)
        .sendChangeTo(creatorAddress.getErgoAddress)
        .build()

      val sTx = dummyProver.sign(uTx)
      logger.info(sTx.toJson(true))
      logger.info(s"Total data: ${mockData.length} entries")
      logger.info(s"Cost: ${sTx.getCost}")
      logger.info(s"updateBoxSize: ${initDataBoxes.head.getBytes.length}")
      logger.info(s"proofBoxSizes: ${initDataBoxes.slice(1, initDataBoxes.length).map(_.getBytes.length).mkString("(" ,", ", ")")}")

  }



}

object ShareStateSuite {
  val mockData = {
    MockAddresses.addresses.sorted.map(
      a =>
      StateMiner(a) -> StateScore(((Math.random() * 100) + 10).toLong, false)
    )
  }

  val partialMockData = mockData.map(m => m._1.toPartialStateMiner -> m._2)
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

  def logger: Logger = LoggerFactory.getLogger("PlasmaTesting")

}
