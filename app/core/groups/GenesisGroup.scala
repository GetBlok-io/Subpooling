package io.getblok.subpooling
package core.groups

import core.boxes.MetadataInputBox
import core.groups.entities.{Pool, Subpool}
import core.groups.models.TransactionGroup
import core.groups.stages.GenesisStage
import global.AppParameters.NodeWallet

import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

class GenesisGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, metadataVal: Long) extends TransactionGroup(pool, ctx, wallet){
  override var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  override var failedGroups:    Map[Subpool, Throwable]         = Map.empty[Subpool, Throwable]
  override val groupName:       String                          = "DistributionGroup"
  var newPools:                 Seq[Subpool]                    = Seq.empty[Subpool]
  override def executeGroup: TransactionGroup = {
    val result = stageManager.execute[MetadataInputBox](new GenesisStage(pool, ctx, wallet, metadataVal))
    completedGroups = result._1.map(r => r._1 -> result._2)
    newPools = result._1.keys.toSeq.sortBy(p => p.id)

    this
  }
}
