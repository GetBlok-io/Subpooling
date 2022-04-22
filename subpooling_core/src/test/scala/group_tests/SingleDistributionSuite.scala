package io.getblok.subpooling_core
package group_tests

import contracts.holding.SimpleHoldingContract
import group_tests.MockData.SingleDistributionData._
import group_tests.MockData._
import group_tests.groups.builders.DistributionBuilder
import group_tests.groups.entities.Subpool
import group_tests.groups.models.GroupBuilder
import group_tests.groups.selectors.StandardSelector
import group_tests.groups.{DistributionGroup, GroupManager}
import registers.PropBytes

import io.getblok.subpooling_core.global.AppParameters
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class SingleDistributionSuite extends AnyFunSuite {
  var group: DistributionGroup = _
  var selector: StandardSelector = _
  var builder: GroupBuilder = _
  var manager: GroupManager = _
  val subPool: Subpool = singlePool.subPools.head
  test("Make DistributionGroup") {
    ergoClient.execute {
      ctx =>

        group = new DistributionGroup(singlePool, ctx, dummyWallet,
          commandContract, holdingContract, getInputBoxes)
        logger.info("holdingContract: " + holdingContract.toAddress.toString)
    }
  }

  test("Make StandardSelector") {
    printMembers(initSingleMembers)
    selector = new StandardSelector(initSingleMembers)
  }

  test("Make DistributionBuilder") {
    builder = new DistributionBuilder(initSingleHoldingMap, Map())
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
        val memberBoxValue = SimpleHoldingContract.getBoxValue(d._2.getScore, subPool.nextTotalScore, initValueAfterFees)
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
        (i.getValue == holdingValue / 100) && (PropBytes.ofErgoTree(i.getErgoTree)(AppParameters.networkType) == PropBytes.ofAddress(creatorAddress)(AppParameters.networkType))
    })
    logger.info(s"tx Cost: ${manager.completedGroups(subPool).getCost}")
  }

  test("Subpool has paid members in outputs") {
    val outputArray = manager.completedGroups(subPool).getOutputsToSpend.asScala.toArray
    subPool.nextDist.dist.foreach{
      d =>
        if(d._2.getStored == 0){
          assert(outputArray.exists(i => PropBytes.ofErgoTree(i.getErgoTree)(AppParameters.networkType) == d._1
            && i.getValue == SimpleHoldingContract.getBoxValue(d._2.getScore, subPool.nextTotalScore, initValueAfterFees)))
        }else{
          assert(!outputArray.exists(i => PropBytes.ofErgoTree(i.getErgoTree)(AppParameters.networkType) == d._1
            && i.getValue == SimpleHoldingContract.getBoxValue(d._2.getScore, subPool.nextTotalScore, initValueAfterFees)))
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
