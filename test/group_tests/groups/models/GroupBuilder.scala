package io.getblok.subpooling
package group_tests.groups.models

import group_tests.groups.{entities, stages}
import global.AppParameters.NodeWallet
import org.ergoplatform.appkit.{BlockchainContext, InputBox}

abstract class GroupBuilder {
  var pool: entities.Pool                  = _
  val stageManager: stages.StageManager  = new stages.StageManager
  def setPool(groupPool: entities.Pool): GroupBuilder = {
    pool = groupPool
    this
  }
  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  def collectGroupInfo: GroupBuilder

  /**
   * Apply special modifications to the entire Transaction Group
   */
  def applyModifications: GroupBuilder

  /**
   * Collect input boxes to be used for each chain's root tx.
   * @param ctx Blockchain Context to execute root transaction in
   * @param wallet Node wallet to use for transaction
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet, inputBoxes: Array[InputBox]): GroupBuilder

  /**
   * Finalize building of the Transaction Group
   */
  def buildGroup: entities.Pool
}
