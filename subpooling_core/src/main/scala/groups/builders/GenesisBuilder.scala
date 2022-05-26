package io.getblok.subpooling_core
package groups.builders

import global.AppParameters.NodeWallet
import groups.models.GroupBuilder

import io.getblok.subpooling_core.groups.entities.Pool
import io.getblok.subpooling_core.groups.stages.roots.GenesisRoot
import org.ergoplatform.appkit.{BlockchainContext, InputBox}

class GenesisBuilder(numSubpools: Long, metadataVal: Long,
                     tokenName: Option[String] = None, tokenDesc: Option[String] = None) extends GroupBuilder {

  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  override def collectGroupInfo: GroupBuilder = {
    this
  }

  /**
   * Apply special modifications to the entire Transaction Group
   */
  override def applyModifications: GroupBuilder = {
    this
  }

  /**
   * Execute the root transaction necessary to begin the Group's Tx Chains
   *
   * @param ctx Blockchain Context to execute root transaction in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet): GroupBuilder = {
    val stageResult = stageManager.execute[InputBox](new GenesisRoot(pool, ctx, wallet, numSubpools, metadataVal, tokenName, tokenDesc))

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
