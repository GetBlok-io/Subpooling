package io.getblok.subpooling_core
package group_tests.groups

import group_tests.groups.models.TransactionGroup

import io.getblok.subpooling_core.contracts.holding.SimpleHoldingContract
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.group_tests.groups.entities.Subpool
import io.getblok.subpooling_core.group_tests.groups.entities.Pool
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolPlacement
import org.ergoplatform.appkit.{BlockchainContext, InputBox, SignedTransaction}

import scala.collection.mutable.ArrayBuffer

class HoldingGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, blockMined: Long, blockReward: Long, inputBoxes: Array[InputBox]) extends TransactionGroup(pool, ctx, wallet, inputBoxes){
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
              d._1.address.toString, d._2.getScore, d._2.getMinPay, d._2.getEpochsMined, minerBoxValue, p.epoch + 1, pool.globalEpoch + 1)
        }
        poolPlacements ++= placements

    }

    completedGroups = pool.subPools.map(p => p -> pool.rootTx).toMap

    this
  }
}
