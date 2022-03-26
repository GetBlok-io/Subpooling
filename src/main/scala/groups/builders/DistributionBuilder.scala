package groups.builders

import boxes.MetadataInputBox
import groups.Pool
import groups.models.GroupBuilder
import org.ergoplatform.appkit.{BlockchainContext, InputBox}

class DistributionBuilder(holdingMap: Map[MetadataInputBox, InputBox], storageMap: Map[MetadataInputBox, InputBox]) extends GroupBuilder{
  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  override def collectGroupInfo: GroupBuilder = {
    for(subPool <- pool.subPools){
      subPool.holdingBox = holdingMap.find(b => b._1.getId == subPool.box.getId).get._2
      subPool.storedBox = storageMap.find(b => b._1.getId == subPool.box.getId).map(o => o._2)
    }
    this
  }

  /**
   * Apply special modifications to the entire Transaction Group
   */
  override def applyModifications: GroupBuilder = {
    pool.subPools.foreach{
      s =>
        s.nextFees = s.box.poolFees
        s.nextInfo = s.box.poolInfo
        s.nextOps  = s.box.poolOps
    }
    this
  }

  /**
   * Initiate the root transactions necessary for each subPool within the Group
   *
   * @param ctx Blockchain Context to initiate transactions in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def initiateRootTransactions(ctx: BlockchainContext): GroupBuilder = ???

  /**
   * Finalize building of the Transaction Group
   */
  override def buildGroup: Pool = ???
}
