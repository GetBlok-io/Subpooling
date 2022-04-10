package io.getblok.subpooling_core
package group_tests.groups.builders

import boxes.BoxHelpers
import contracts.holding.HoldingContract
import global.AppParameters.NodeWallet
import group_tests.groups.entities.Pool
import group_tests.groups.models.GroupBuilder
import group_tests.groups.stages.HoldingRoot

import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox}

class HoldingBuilder(blockReward: Long, holdingContract: HoldingContract, baseFeeMap: Map[Address, Long]) extends GroupBuilder{
  var poolShareScore: Long = 0
  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  override def collectGroupInfo: GroupBuilder = {
    poolShareScore = pool.subPools.map(s => s.nextTotalScore).sum
    this
  }

  /**
   * Apply special modifications to the entire Transaction Group
   */
  override def applyModifications: GroupBuilder = {
    val rewardAfterBaseFee = blockReward - baseFeeMap.values.sum
    pool.subPools.foreach{
      s =>
        s.nextHoldingValue = BoxHelpers.removeDust(((BigDecimal(s.nextTotalScore) / BigDecimal(poolShareScore)) * rewardAfterBaseFee).toLong)
    }
    this
  }

  /**
   * Execute the root transaction necessary to begin the Group's Tx Chains
   *
   * @param ctx Blockchain Context to execute root transaction in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet, inputBoxes: Array[InputBox]): GroupBuilder = {
    val stageResult = stageManager.execute[InputBox](new HoldingRoot(pool, ctx, wallet, holdingContract, baseFeeMap, inputBoxes))
    pool.subPools.foreach{
      p =>
        p.rootBox = stageResult._1(p)
    }
    pool.rootTx = stageResult._2
    this
  }

  /**
   * Finalize building of the Transaction Group
   */
  override def buildGroup: Pool = {
    pool
  }
}
