package io.getblok.subpooling_core
package groups.builders

import io.getblok.subpooling_core.boxes.BoxHelpers
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, SimpleHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.entities.Pool
import io.getblok.subpooling_core.groups.models.GroupBuilder
import io.getblok.subpooling_core.groups.stages.HoldingRoot
import io.getblok.subpooling_core.persistence.models.Models.PoolInformation
import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox}

class HoldingBuilder(blockReward: Long, holdingContract: HoldingContract, baseFeeMap: Map[Address, Long],
                     var inputBoxes: Option[Seq[InputBox]] = None) extends GroupBuilder {
  var poolShareScore: Long = 0

  var _rootStage: HoldingRoot = _
  def getRoot(ctx: BlockchainContext, wallet: NodeWallet): HoldingRoot = {
    if(_rootStage == null)
      _rootStage = new HoldingRoot(pool, ctx, wallet, holdingContract, baseFeeMap, inputBoxes)
    _rootStage
  }
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
    pool.subPools.foreach {
      s =>
        s.nextHoldingValue = {
            BoxHelpers.removeDust(((BigDecimal(s.nextTotalScore) / BigDecimal(poolShareScore)) * rewardAfterBaseFee).toLong)
        }
    }
    this
  }

  /**
   * Execute the root transaction necessary to begin the Group's Tx Chains
   *
   * @param ctx Blockchain Context to execute root transaction in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet): GroupBuilder = {
    val stageResult = stageManager.execute[InputBox](getRoot(ctx, wallet))
    pool.subPools.foreach {
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
