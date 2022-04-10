package io.getblok.subpooling_core
package group_tests.groups.models

import global.AppParameters.NodeWallet
import group_tests.groups.{chains, entities, stages}

import org.ergoplatform.appkit.{BlockchainContext, InputBox, SignedTransaction}

abstract class TransactionGroup(pool: entities.Pool, ctx: BlockchainContext, wallet: NodeWallet, inputBoxes: Array[InputBox]) {
  var completedGroups: Map[entities.Subpool, SignedTransaction]
  var failedGroups:    Map[entities.Subpool, Throwable]
  val groupName:       String

  val stageManager:    stages.StageManager = new stages.StageManager
  val chainManager:    chains.ChainManager = new chains.ChainManager

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
      .executeRootTx(ctx, wallet, inputBoxes)
      .buildGroup

    this
  }

  def executeGroup: TransactionGroup

  def removeFromGroup(subpool: entities.Subpool): Unit = {
    pool.subPools -= subpool
  }
}
