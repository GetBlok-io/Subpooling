package groups.models

import groups.Pool
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
   * Initiate the root transactions necessary for each subPool within the Group
   * @param ctx Blockchain Context to initiate transactions in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  def initiateRootTransactions(ctx: BlockchainContext): GroupBuilder

  /**
   * Finalize building of the Transaction Group
   */
  def buildGroup: Pool
}
