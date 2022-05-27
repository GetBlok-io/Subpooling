package io.getblok.subpooling_core
package groups

import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, HoldingContract, SimpleHoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.models.TransactionGroup
import io.getblok.subpooling_core.persistence.models.Models.PoolPlacement
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer

class HoldingGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, blockMined: Long, holdingContract: HoldingContract) extends TransactionGroup(pool, ctx, wallet) {
  override var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  override var failedGroups: Map[Subpool, Throwable] = Map.empty[Subpool, Throwable]
  override val groupName: String = "HoldingGroup"
  val poolPlacements: ArrayBuffer[PoolPlacement] = ArrayBuffer.empty[PoolPlacement]
  private val logger = LoggerFactory.getLogger(groupName)


  override def executeGroup: TransactionGroup = {
    holdingContract match {
      case contract: SimpleHoldingContract =>
        pool.subPools.foreach {
          p =>
            val poolTxFee = SimpleHoldingContract.getTxFee(p.nextDist)
            logger.info(s"Next pool tx fee for subpool ${p.id}: $poolTxFee")
            val poolValAfterFees = SimpleHoldingContract.getValAfterFees(p.nextHoldingValue, poolTxFee, p.box.poolFees)
            logger.info(s"Next val after fees for subpool ${p.id}: $poolValAfterFees")
            val placements = p.nextDist.dist.map {
              d =>
                val minerBoxValue = SimpleHoldingContract.getBoxValue(d._2.getScore, p.nextTotalScore, poolValAfterFees)
                logger.info(s"Next box val for miner ${d._1.address}: $minerBoxValue")
                PoolPlacement(p.token.toString, p.id, blockMined, p.rootBox.getId.toString, p.nextHoldingValue,
                  d._1.address.toString, d._2.getScore, d._2.getMinPay, d._2.getEpochsMined, minerBoxValue, p.epoch + 1, pool.globalEpoch)
            }
            poolPlacements ++= placements

        }

        completedGroups = pool.subPools.map(p => p -> pool.rootTx).toMap

        this
      case contract: TokenHoldingContract =>
        logger.info("Using TokenHoldingContract in group!")
        pool.subPools.foreach {
          p =>
            val poolTxFee = TokenHoldingContract.getTxFee(p.nextDist)
            logger.info(s"Next pool tx fee for subpool ${p.id}: $poolTxFee")
            val poolValAfterFees = TokenHoldingContract.getValAfterFees(p.nextHoldingValue, poolTxFee, p.box.poolFees)
            logger.info(s"Next val after fees for subpool ${p.id}: $poolValAfterFees")
            val placements = p.nextDist.dist.map {
              d =>
                val minerBoxValue = TokenHoldingContract.getBoxValue(d._2.getScore, p.nextTotalScore, poolValAfterFees)
                logger.info(s"Next box val for miner ${d._1.address}: $minerBoxValue")
                PoolPlacement(p.token.toString, p.id, blockMined, p.rootBox.getId.toString, p.nextHoldingValue,
                  d._1.address.toString, d._2.getScore, d._2.getMinPay, d._2.getEpochsMined, minerBoxValue, p.epoch + 1, pool.globalEpoch)
            }
            poolPlacements ++= placements

        }
        completedGroups = pool.subPools.map(p => p -> pool.rootTx).toMap
        this
      case contract: AdditiveHoldingContract =>
        pool.subPools.foreach {
          p =>
            val poolTxFee = AdditiveHoldingContract.getTxFee(p.nextDist)
            logger.info(s"Next pool tx fee for subpool ${p.id}: $poolTxFee")
            val poolValAfterFees = AdditiveHoldingContract.getValAfterFees(p.nextHoldingValue, poolTxFee, p.box.poolFees)
            logger.info(s"Next val after fees for subpool ${p.id}: $poolValAfterFees")
            val placements = p.nextDist.dist.map {
              d =>
                val minerBoxValue = AdditiveHoldingContract.getBoxValue(d._2.getScore, p.nextTotalScore, poolValAfterFees)
                logger.info(s"Next box val for miner ${d._1.address}: $minerBoxValue")
                PoolPlacement(p.token.toString, p.id, blockMined, p.rootBox.getId.toString, p.nextHoldingValue,
                  d._1.address.toString, d._2.getScore, d._2.getMinPay, d._2.getEpochsMined, minerBoxValue, p.epoch + 1, pool.globalEpoch)
            }
            poolPlacements ++= placements

        }
        completedGroups = pool.subPools.map(p => p -> pool.rootTx).toMap
        this
    }
  }

}
