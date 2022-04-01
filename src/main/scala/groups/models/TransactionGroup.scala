package groups.models

import app.AppParameters.NodeWallet
import groups.chains.ChainManager
import groups.entities.{Pool, Subpool}
import groups.stages.StageManager
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

abstract class TransactionGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet) {
  var completedGroups: Map[Subpool, SignedTransaction]
  var failedGroups:    Map[Subpool, Throwable]
  val groupName:       String

  val stageManager:    StageManager = new StageManager
  val chainManager:    ChainManager = new ChainManager

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
