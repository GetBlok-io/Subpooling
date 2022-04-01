package groups.builders

import app.AppParameters.NodeWallet
import boxes.{BoxHelpers, MetadataInputBox}
import contracts.holding.HoldingContract
import groups.entities.Pool
import groups.models.GroupBuilder
import groups.stages.{DistributionRoot, HoldingRoot}
import org.ergoplatform.appkit.{BlockchainContext, InputBox}

class HoldingBuilder(blockReward: Long, holdingContract: HoldingContract) extends GroupBuilder{
  var poolShareScore: Long = 0
  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  override def collectGroupInfo: GroupBuilder = {
    poolShareScore = pool.subPools.map(s => s.nextTotalScore).sum
    this
  }

  /**
   * Apply special modifications to the entire Transaction Group
   */
  override def applyModifications: GroupBuilder = {
    pool.subPools.foreach{
      s =>
        s.nextHoldingValue = BoxHelpers.removeDust(((BigDecimal(s.nextTotalScore) / BigDecimal(poolShareScore)) * blockReward).toLong)
    }
    this
  }

  /**
   * Execute the root transaction necessary to begin the Group's Tx Chains
   *
   * @param ctx Blockchain Context to execute root transaction in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet): GroupBuilder = {
    val stageResult = stageManager.execute[InputBox](new HoldingRoot(pool, ctx, wallet, holdingContract))
    pool.subPools.foreach{
      p =>
        p.rootBox = stageResult._1(p)
    }
    pool.rootTx = stageResult._2
    this
  }

  /**
   * Finalize building of the Transaction Group
   */
  override def buildGroup: Pool = {
    pool
  }
}
