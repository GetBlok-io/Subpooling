package io.getblok.subpooling_core
package groups

import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.models.TransactionGroup
import io.getblok.subpooling_core.groups.stages.GenesisStage
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

class GenesisGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, metadataVal: Long, feeValue: Int = 1000) extends TransactionGroup(pool, ctx, wallet) {
  override var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  override var failedGroups: Map[Subpool, Throwable] = Map.empty[Subpool, Throwable]
  override val groupName: String = "GenesisGroup"
  var newPools: Seq[Subpool] = Seq.empty[Subpool]

  override def executeGroup: TransactionGroup = {
    val result = stageManager.execute[MetadataInputBox](new GenesisStage(pool, ctx, wallet, metadataVal, feeValue))
    completedGroups = result._1.map(r => r._1 -> result._2)
    newPools = result._1.keys.toSeq.sortBy(p => p.id)

    this
  }
}
