package groups

import app.AppParameters.NodeWallet
import boxes.{CommandInputBox, MetadataInputBox}
import contracts.command.CommandContract
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import groups.chains.DistributionChain
import groups.entities.{Pool, Subpool}
import groups.models.TransactionGroup
import groups.stages.CommandStage
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}
import persistence.models.Models.PoolPlacement
import registers.PropBytes

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
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
