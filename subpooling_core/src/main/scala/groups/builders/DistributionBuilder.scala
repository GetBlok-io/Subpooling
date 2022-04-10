package io.getblok.subpooling_core
package groups.builders

import boxes.MetadataInputBox
import global.AppParameters.NodeWallet
import groups.models.GroupBuilder
import groups.stages.DistributionRoot

import io.getblok.subpooling_core.groups.entities.Pool
import org.ergoplatform.appkit.{BlockchainContext, InputBox}

class DistributionBuilder(holdingMap: Map[MetadataInputBox, InputBox], storageMap: Map[MetadataInputBox, InputBox]) extends GroupBuilder {

  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  override def collectGroupInfo: GroupBuilder = {
    for (subPool <- pool.subPools) {
      subPool.holdingBox = holdingMap.find(b => b._1.getId == subPool.box.getId).get._2
      subPool.storedBox = storageMap.find(b => b._1.getId == subPool.box.getId).map(o => o._2)
    }
    this
  }

  /**
   * Apply special modifications to the entire Transaction Group
   */
  override def applyModifications: GroupBuilder = {
    pool.subPools.foreach {
      s =>
        s.nextFees = s.box.poolFees
        s.nextInfo = s.box.poolInfo
        s.nextOps = s.box.poolOps
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
    val stageResult = stageManager.execute[InputBox](new DistributionRoot(pool, ctx, wallet))
    pool.subPools.foreach {
      p =>
        p.rootBox = stageResult._1(p)
    }

    this
  }

  /**
   * Finalize building of the Transaction Group
   */
  override def buildGroup: Pool = {
    pool
  }
}
