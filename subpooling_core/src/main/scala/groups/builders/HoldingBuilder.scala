package io.getblok.subpooling_core
package groups.builders

import io.getblok.subpooling_core.boxes.BoxHelpers
import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, HoldingContract, SimpleHoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.entities.Pool
import io.getblok.subpooling_core.groups.models.{GroupBuilder, TransactionStage}
import io.getblok.subpooling_core.groups.stages.roots.HoldingRoot
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolInformation
import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox}
import org.slf4j.{Logger, LoggerFactory}

class HoldingBuilder(rewardPaid: Long, holdingContract: HoldingContract, baseFeeMap: Map[Address, Long], rootStage: TransactionStage[InputBox],
                     var inputBoxes: Option[Seq[InputBox]] = None) extends GroupBuilder {
  var poolShareScore: Long = 0
  private val logger: Logger = LoggerFactory.getLogger("HoldingBuilder")
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
    val rewardAfterBaseFee = rewardPaid - baseFeeMap.values.sum
    logger.info(s"Total reward: ${Helpers.nanoErgToErg(rewardPaid)} ERG")
    logger.info(s"Reward after base fee: ${Helpers.nanoErgToErg(rewardAfterBaseFee)} ERG")

    pool.subPools.foreach {
      s =>
        s.nextHoldingValue =
          holdingContract match {
            case contract: SimpleHoldingContract =>
              // Dust is removed and reward paid has base fee taken away, this is because reward paid is in ERG
              BoxHelpers.removeDust(((BigDecimal(s.nextTotalScore) / BigDecimal(poolShareScore)) * rewardAfterBaseFee).toLong)
            case contract: TokenHoldingContract =>
              // No base fees taken from reward paid, as reward paid is actually
              // Just the emissions reward
              // CAUTION: Usage of emission exchange contract makes this calculation obsolete, as nextHoldingValue is
              // calculated after the emission cycle.
              ((BigInt(s.nextTotalScore) / BigInt(poolShareScore)) * rewardPaid).toLong
            case contract: AdditiveHoldingContract =>
              BoxHelpers.removeDust(((BigDecimal(s.nextTotalScore) / BigDecimal(poolShareScore)) * rewardAfterBaseFee).toLong)
          }

        s.nextHoldingShare = s.nextTotalScore

    }
    val poolsToRemove = pool.subPools.filter(s => s.nextHoldingShare == 0)
    logger.info(s"Current number of pools before modification: ${pool.subPools.length} \n " +
      s"Now removing ${poolsToRemove.length} pools with a holding value equal to 0")
    pool.subPools --= poolsToRemove
    this
  }

  /**
   * Execute the root transaction necessary to begin the Group's Tx Chains
   *
   * @param ctx Blockchain Context to execute root transaction in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet): GroupBuilder = {
    val stageResult = stageManager.execute[InputBox](rootStage)
    pool.subPools.foreach {
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
