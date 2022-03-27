package groups.models

import app.AppParameters.NodeWallet
import groups.entities.Pool
import org.ergoplatform.appkit.BlockchainContext

abstract class GroupBuilder {
  var pool: Pool = _

  def setPool(groupPool: Pool): GroupBuilder = {
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
  def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet): GroupBuilder

  /**
   * Finalize building of the Transaction Group
   */
  def buildGroup: Pool
}
