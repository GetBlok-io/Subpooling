package group_test

import group_test.MockData.HoldingData._
import group_test.MockData.{additiveHoldingContract, dummyWallet, ergoClient, holdingContract}
import io.getblok.subpooling_core.boxes.{ExchangeEmissionsBox, ProportionalEmissionsBox}
import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.groups.builders.HoldingBuilder
import io.getblok.subpooling_core.groups.models.GroupBuilder
import io.getblok.subpooling_core.groups.selectors.{SelectionParameters, StandardSelector}
import io.getblok.subpooling_core.groups.stages.roots.{ExchangeEmissionsRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.groups.{GroupManager, HoldingGroup, entities}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class ProportionalHoldingSuite extends AnyFunSuite {
  var root: ProportionalEmissionsRoot = _
  var group: HoldingGroup = _
  var selector: StandardSelector = _
  var builder: GroupBuilder = _
  var manager: GroupManager = _
  val subPool: entities.Subpool = singlePool.subPools.head
  val blockHeight: Long = 1L
  var emissionsBox: ProportionalEmissionsBox = _

  private val logger: Logger = LoggerFactory.getLogger("TokenHoldingSuite")
  test("Make HoldingGroup") {
    ergoClient.execute {
      ctx =>

        group = new HoldingGroup(singlePool, ctx, dummyWallet, blockHeight, additiveHoldingContract)
        logger.info("holdingContract: " + additiveHoldingContract.toAddress.toString)
    }
  }

  test("Make StandardSelector") {
    printMembers(initSingleMembers)
    selector = new StandardSelector(initSingleMembers, SelectionParameters())
  }

  test("Make Emissions Box"){
    emissionsBox = buildProportionBox(totalEmissions, additiveHoldingContract)
  }

  test("Make Emissions Root") {
    ergoClient.execute{
      ctx =>
        root = new ProportionalEmissionsRoot(singlePool, ctx, dummyWallet, additiveHoldingContract, holdingValue, baseFeeMap, emissionsBox,
          Some(getInputBoxes), false)
    }

  }

  test("Make HoldingBuilder") {
    builder = new HoldingBuilder(holdingValue, additiveHoldingContract, baseFeeMap, root)
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

  test("Subpool has root box") {
    assert(subPool.rootBox != null, "Subpool's root box not set!")
  }

  test("Subpool has nextHoldingValue"){
    assert(subPool.nextHoldingValue != 0)
  }

  test("Subpool root box has same value as nextHoldingValue"){
    assert(subPool.rootBox.getValue == subPool.nextHoldingValue)
  }

  test("Pool has rootTx"){
    assert(singlePool.rootTx != null)
  }

  test("Subpool has nextDist defined"){
    assert(subPool.nextDist != null)
  }
  test("Subpool nextDist contains all members"){
    assert(subPool.nextDist.dist.forall{
      d =>
        initSingleMembers.exists(m => m.toDistributionValue._1 == d._1)
    })
  }


  test("baseFeeMap was removed from rootBox value"){
    subPool.rootBox.getValue == holdingValue - (baseFeeMap.head._2)
    logger.info(s"BaseFeeMap: $baseFeeMap")
    logger.info("rootBox value: " + subPool.rootBox.getValue.toString)
  }

  test("baseFeeMap was present in rootTx"){
    singlePool.rootTx.getOutputsToSpend.asScala.toArray.exists{
      p => p.getValue == baseFeeMap.head._2 && (p.getErgoTree.bytes sameElements baseFeeMap.head._1.getErgoAddress.script.bytes)
    }
  }


  test("Group has placements"){
    assert(group.poolPlacements.nonEmpty)
  }

  test("Group placements valid"){
    group.poolPlacements.foreach{
      p =>

        assert(p.subpool == subPool.token.toString, "Placement subpool token is correct")


        assert(p.subpool_id == subPool.id, "Placement subpool id is correct")


        assert(p.holding_id == subPool.rootBox.getId.toString, "holding_id matches with rootBox")


        assert(p.holding_val == subPool.rootBox.getValue, "Holding value matches with rootBox")

        val asMember = initSingleMembers.find(m => m.address.toString == p.miner)

        assert(asMember.isDefined, "Miner not found in placements")


        assert(asMember.get.shareScore == p.score, "Share score not preserved")


        assert(asMember.get.epochsMined + 1 == p.epochs_mined,
          "Epochs_mined is incorrect")


        assert(asMember.get.minPay == p.minpay, "Minpay not preserved")


        assert(p.block == blockHeight, "blockHeight is not preserved")

//        assert(p.amount == AdditiveHoldingContract.getBoxValue(asMember.get.shareScore, subPool.nextTotalScore, holdingValue - ((holdingValue * 1000) / 100000)),
//          "Amount added for miner was incorrect")


    }
  }

}
