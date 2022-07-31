package group_test

import io.getblok.subpooling_core.contracts.holding.TokenHoldingContract
import io.getblok.subpooling_core.explorer.ExplorerHandler
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups._
import io.getblok.subpooling_core.groups.builders.DistributionBuilder
import io.getblok.subpooling_core.groups.entities.Subpool
import io.getblok.subpooling_core.groups.models.GroupBuilder
import io.getblok.subpooling_core.groups.selectors.{SelectionParameters, StandardSelector}
import io.getblok.subpooling_core.registers.PropBytes
import MockData.SingleDistributionData._
import MockData._
import io.getblok.subpooling_core.contracts.emissions.HybridExchangeContract
import org.ergoplatform.appkit.{ErgoId, NetworkType}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class EmissionExchangeTest extends AnyFunSuite {
  var group: DistributionGroup = _
  var selector: StandardSelector = _
  var builder: GroupBuilder = _
  var manager: GroupManager = _
  val subPool: Subpool = singlePool.subPools.head
  val explorerHandler: ExplorerHandler = new ExplorerHandler(NetworkType.MAINNET)
  test("Make DistributionGroup") {
    ergoClient.execute {
      ctx =>

        group = new DistributionGroup(singlePool, ctx, dummyWallet,
          commandContract, holdingContract, None, false)
        logger.info("holdingContract: " + holdingContract.toAddress.toString)
    }
  }

  test("Get NETA Pool"){
    val boxItems = explorerHandler.boxesByTokenId(ErgoId.create("d7868533f26db1b1728c1f85c2326a3c0327b57ddab14e41a2b77a5d4c20f4b2"))
    val box = boxItems.get.head
    val POOL_FEE_DENOM = 1000
    logger.info(box.toString)
    val swapAmount = HybridExchangeContract.calculateMinOutputAmount(Helpers.ergToNanoErg(65), .01,
      box.value, box.assets(2).amount , Integer.valueOf(box.registers.R4.get.renderedValue).longValue() , POOL_FEE_DENOM)

    logger.info(s"Swap amount: $swapAmount")
  }
  // 29178820000
  def calculateMinOutputAmount(baseAmount: Long, maxSlippagePercentage: Double, xAssetAmount: Long, yAssetAmount: Long, feeNumerator: Long, feeDenominator: Long): Long = {
    val swapInputAmount:  BigInt = BigInt.apply(baseAmount)
    val xAmount:          BigInt = BigInt.apply(xAssetAmount)
    val yAmount:          BigInt = BigInt.apply(yAssetAmount)
    val feeNum:           BigInt = BigInt.apply(feeNumerator)
    val feeDenom:   BigInt = BigInt.apply(feeDenominator)

    val slippage: BigInt = BigInt.apply((maxSlippagePercentage * 100D).toInt)
    //val outputAmount: BigInt = (yAmount * swapInputAmount * feeNum) / ((xAmount + ((xAmount * slippage) / (BigInt.apply(100) * BigInt.apply(100)))) * feeDenom + swapInputAmount * feeNum)
    val outputNum = ((yAssetAmount / 100000) * ((baseAmount * feeNumerator) / 100000))
    logger.info(s"OutputNumerator: ${outputNum}")
    val outputDenom = (((xAssetAmount + ((xAssetAmount * 1) / 10000)) * feeDenominator) + (baseAmount * feeNumerator)) / 100000
    logger.info(s"OutputDenominator: ${outputDenom}")
    val unadjustedOutput = outputNum / outputDenom
    logger.info(s"unAdj Output: ${unadjustedOutput}")
    val reAdjOutput = unadjustedOutput * 100000
    logger.info(s"Output reAdj: ${reAdjOutput}")
    val outputAmountLong: Long = reAdjOutput.toLong
    val percentAdded = outputAmountLong + ((outputAmountLong * 10000)/ 100000)
    logger.info(s"With percent added: ${percentAdded}")
    outputAmountLong
  }

  test("Make StandardSelector") {
    printMembers(initSingleMembers)
    selector = new StandardSelector(initSingleMembers, SelectionParameters())
  }

  test("Make DistributionBuilder") {
    builder = new DistributionBuilder(initSingleHoldingMap, Map(), Some(getInputBoxes), false)
  }

  test("Make GroupManager") {
    manager = new GroupManager(group, builder, selector)
  }

  test("Initiate Manager") {
    logger.info(singlePool.subPools.head.info.toString)
    manager.initiate()
  }

  test("Manager is Success") {
    assert(manager.isSuccess)
    logger.info(manager.completedGroups.head._2.toJson(true))
    logger.info("Tx Cost: " + manager.completedGroups.head._2.getCost)

  }

  test("Subpool has next box") {
    assert(subPool.nextBox != null, "Subpool's next box not set!")
  }

  test("Subpool has correct pool info") {

    assert(subPool.nextBox.poolInfo.getEpoch == 1)
    assert(subPool.nextBox.poolInfo.getSubpool == 0)
    logger.info(subPool.nextBox.poolInfo.toString)
  }

  test("Subpool has correct share distribution") {
    logger.info(subPool.nextDist.toString)
    subPool.nextDist.dist.foreach {
      d =>
        val asMember = initSingleMembers.find(m => m.toDistributionValue._1 == d._1)
        assert(asMember.isDefined, "Member not defined")
        assert(asMember.get.shareScore == d._2.getScore, "Share scores not equal")
        assert(asMember.get.minPay == d._2.getMinPay, "Min Pays changed")
        assert(d._2.getEpochsMined == 1, "Epoch mined not increased")
        val memberBoxValue = TokenHoldingContract.getBoxValue(d._2.getScore, subPool.nextTotalScore, initValueAfterFees)
        if (memberBoxValue < d._2.getMinPay)
          assert(d._2.getStored == memberBoxValue, "Stored value incorrect")
        else
          assert(d._2.getStored == 0, "Stored value not 0")

    }
  }

  test("Subpool has correct pool fees") {
    val assertion = subPool.nextFees.fees.forall {
      f =>
        subPool.box.poolFees.fees.exists(fees => fees._1 == f._1 && fees._2 == f._2)
    }
    assert(assertion, "Pool fees not equal")
  }

  test("Subpool has correct pool ops") {
    val assertion = subPool.nextOps.arr.forall {
      o =>
        subPool.box.poolOps.arr.contains(o)
    }
    assert(assertion, "Pool ops not equal")
  }

  test("Subpool found in success map") {
    assert(manager.completedGroups.contains(subPool))
  }

  test("Subpool has fee box in outputs") {
    assert(manager.completedGroups(subPool).getOutputsToSpend.asScala.toArray.exists {
      i =>
        (i.getTokens.get(0).getValue == holdingValue / 100) && (PropBytes.ofErgoTree(i.getErgoTree)(AppParameters.networkType) == PropBytes.ofAddress(creatorAddress)(AppParameters.networkType))
    })
    logger.info(s"tx Cost: ${manager.completedGroups(subPool).getCost}")
  }

  test("Subpool has paid members in outputs") {
    val outputArray = manager.completedGroups(subPool).getOutputsToSpend.asScala.toArray
    subPool.nextDist.dist.foreach{
      d =>
        if(d._2.getStored == 0){
          assert(outputArray.exists(i => PropBytes.ofErgoTree(i.getErgoTree)(AppParameters.networkType) == d._1
            && i.getTokens.get(0).getValue == TokenHoldingContract.getBoxValue(d._2.getScore, subPool.nextTotalScore, initValueAfterFees)))
        }else{
          assert(!outputArray.exists(i => PropBytes.ofErgoTree(i.getErgoTree)(AppParameters.networkType) == d._1
            && i.getTokens.get(0).getValue == TokenHoldingContract.getBoxValue(d._2.getScore, subPool.nextTotalScore, initValueAfterFees)))
        }
    }
  }

  test("Subpool has payment map filled"){
    val outputArray = manager.completedGroups(subPool).getOutputsToSpend.asScala.toArray
    subPool.paymentMap.foreach{
      p =>
        assert(subPool.nextDist.dist.contains(p._1), s"Subpool does not contain propbytes of ${p._1.address} in payment map")
        assert(subPool.nextDist.dist(p._1).getStored == 0, "Member is in payment map but does not have stored value of 0")
        assert(outputArray.exists(i => i.getId == p._2.getId))
    }
  }
}
