package io.getblok.subpooling_core
package groups.models

import groups.entities.{Pool, Subpool}

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.chains.ChainManager
import io.getblok.subpooling_core.groups.stages.StageManager
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

abstract class TransactionGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet) {
  var completedGroups: Map[Subpool, SignedTransaction]
  var failedGroups: Map[Subpool, Throwable]
  val groupName: String

  val stageManager: StageManager = new StageManager
  val chainManager: ChainManager = new ChainManager

  def selectForGroup(selector: GroupSelector): TransactionGroup = {
    selector
      .setPool(pool)
      .getSelection
    this
  }

  def buildGroup(builder: GroupBuilder): TransactionGroup = {
    builder
      .setPool(pool)
      .collectGroupInfo
      .applyModifications
      .executeRootTx(ctx, wallet)
      .buildGroup

    this
  }

  def executeGroup: TransactionGroup

  def removeFromGroup(subpool: Subpool): Unit = {
    pool.subPools -= subpool
  }
}
