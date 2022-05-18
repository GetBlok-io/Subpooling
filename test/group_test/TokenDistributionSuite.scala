package group_test

import MockData.SingleDistributionData._
import MockData._
import io.getblok.subpooling_core.contracts.holding.TokenHoldingContract
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.groups._
import io.getblok.subpooling_core.groups.builders.DistributionBuilder
import io.getblok.subpooling_core.groups.entities.Subpool
import io.getblok.subpooling_core.groups.models.GroupBuilder
import io.getblok.subpooling_core.groups.selectors.{SelectionParameters, StandardSelector}
import io.getblok.subpooling_core.registers.PropBytes
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class TokenDistributionSuite extends AnyFunSuite {
  var group: DistributionGroup = _
  var selector: StandardSelector = _
  var builder: GroupBuilder = _
  var manager: GroupManager = _
  val subPool: Subpool = singlePool.subPools.head
  test("Make DistributionGroup") {
    ergoClient.execute {
      ctx =>

        group = new DistributionGroup(singlePool, ctx, dummyWallet,
          commandContract, holdingContract, false)
        logger.info("holdingContract: " + holdingContract.toAddress.toString)
    }
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
