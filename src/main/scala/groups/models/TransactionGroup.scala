package groups.models

import app.AppParameters.NodeWallet
import groups.entities.{Pool, Subpool}
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

abstract class TransactionGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet) {
  var completedGroups: Map[Subpool, SignedTransaction]
  var failedGroups:    Map[Subpool, Throwable]

  def selectForGroup(selector: GroupSelector): TransactionGroup = {
    selector
      .setPool(pool)
      .placeCurrentMiners
      .evaluateLostMiners
      .placeNewMiners
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
