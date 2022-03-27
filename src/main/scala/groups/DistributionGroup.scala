package groups

import app.AppParameters.NodeWallet
import groups.entities.{Pool, Subpool}
import groups.models.TransactionGroup
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

class DistributionGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet) extends TransactionGroup(pool, ctx, wallet){
  override var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  override var failedGroups:    Map[Subpool, Throwable]         = Map.empty[Subpool, Throwable]

  override def executeGroup: TransactionGroup = {
    this
  }
}
