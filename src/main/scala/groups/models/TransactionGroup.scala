package groups.models

import groups.{Pool, Subpool}
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

abstract class TransactionGroup(pool: Pool, ctx: BlockchainContext) {
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
      .initiateRootTransactions(ctx)
      .buildGroup

    this
  }

  def executeGroup: TransactionGroup

  def removeFromGroup(subpool: Subpool): Unit = {
    pool.subPools -= subpool
  }
}
