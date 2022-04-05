package io.getblok.subpooling
package core.groups

import core.contracts.holding.SimpleHoldingContract
import core.groups.entities.{Pool, Subpool}
import core.groups.models.TransactionGroup
import core.persistence.models.Models.PoolPlacement
import global.AppParameters.NodeWallet

import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

import scala.collection.mutable.ArrayBuffer

class HoldingGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, blockMined: Long, blockReward: Long) extends TransactionGroup(pool, ctx, wallet){
  override var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  override var failedGroups:    Map[Subpool, Throwable]         = Map.empty[Subpool, Throwable]
  override val groupName:       String                          = "HoldingGroup"
  val poolPlacements:           ArrayBuffer[PoolPlacement]      = ArrayBuffer.empty[PoolPlacement]

  override def executeGroup: TransactionGroup = {

    pool.subPools.foreach{
      p =>
        val poolTxFee = SimpleHoldingContract.getTxFee(p.nextDist)
        val poolValAfterFees = SimpleHoldingContract.getValAfterFees(blockReward, poolTxFee, p.box.poolFees)

        val placements = p.nextDist.dist.map{
          d =>
            val minerBoxValue = SimpleHoldingContract.getBoxValue(d._2.getScore, p.nextTotalScore, poolValAfterFees)

            PoolPlacement(p.token.toString, p.id, blockMined, p.rootBox.getId.toString, p.nextHoldingValue,
              d._1.address.toString, d._2.getScore, d._2.getMinPay, d._2.getEpochsMined, minerBoxValue)
        }
        poolPlacements ++= placements

    }

    completedGroups = pool.subPools.map(p => p -> pool.rootTx).toMap

    this
  }
}
