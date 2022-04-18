package io.getblok.subpooling_core
package groups

import boxes.{CommandInputBox, MetadataInputBox}

import io.getblok.subpooling_core.contracts.command.CommandContract
import io.getblok.subpooling_core.contracts.holding.HoldingContract
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.chains.DistributionChain
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.models.TransactionGroup
import io.getblok.subpooling_core.groups.stages.CommandStage
import io.getblok.subpooling_core.payments.ShareStatistics
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolMember, PoolState}
import io.getblok.subpooling_core.registers.{MemberInfo, PropBytes}
import org.ergoplatform.appkit.{BlockchainContext, NetworkType, SignedTransaction}

import java.time.LocalDateTime
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer

class DistributionGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet,
                        commandContract: CommandContract, holdingContract: HoldingContract, sendTxs: Boolean = true) extends TransactionGroup(pool, ctx, wallet) {
  override var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  override var failedGroups: Map[Subpool, Throwable] = Map.empty[Subpool, Throwable]
  override val groupName: String = "DistributionGroup"

  override def executeGroup: TransactionGroup = {
    val result = stageManager.execute[CommandInputBox](new CommandStage(pool, ctx, wallet, commandContract, holdingContract, sendTxs))
    implicit val networkType: NetworkType = ctx.getNetworkType
    pool.subPools.foreach {
      p =>
        p.commandBox = result._1(p)
        p.nextDist = result._1(p).shareDistribution
    }

    val resultSet = chainManager.execute[MetadataInputBox](new DistributionChain(pool, ctx, wallet, holdingContract, sendTxs))

    pool.subPools --= resultSet._2.keys

    pool.subPools.foreach{
      p =>
        p.nextBox = resultSet._1(p)._2
        val paymentMap = p.nextDist.dist.map {
          d => d._1 -> resultSet._1(p)._1.getOutputsToSpend.asScala.toArray.find(i => PropBytes.ofErgoTree(i.getErgoTree) == d._1)
        }
        p.paymentMap = paymentMap.filter(p => p._2.isDefined).map(p => p._1 -> p._2.get)
        p.nextStorage = resultSet._1(p)._1.getOutputsToSpend.asScala.toArray.find(i =>
          PropBytes.ofErgoTree(holdingContract.getErgoTree) == PropBytes.ofErgoTree(i.getErgoTree))

    }



    completedGroups = resultSet._1.map(p => p._1 -> p._2._1)
    failedGroups    = resultSet._2

    this
  }
  // TODO calculate from netDiff from share score instead
  def getNextPoolMembers(block: Block): ArrayBuffer[PoolMember] = {
    val poolMembers: ArrayBuffer[PoolMember] = ArrayBuffer.empty[PoolMember]
    val totalPoolScore = pool.subPools.map(p => p.nextTotalScore).sum
    completedGroups.keys.foreach {
      p =>
        p.nextDist.dist.foreach {
          d =>
            val sharePerc = (BigDecimal(d._2.getScore) / totalPoolScore).toDouble
            val shareNum  = ((d._2.getScore * block.netDiff) / AppParameters.scoreAdjustmentCoeff).toLong
            val change = getAmountAdded(p, d)
            if (p.paymentMap.contains(d._1)) {
              val poolMember = PoolMember(p.token.toString, p.id, completedGroups(p).getId.replace("\"", ""),
                p.nextBox.getId.toString, pool.globalEpoch + 1, p.nextBox.epoch,
                p.nextBox.epochHeight, d._1.address.toString, d._2.getScore, shareNum, sharePerc, d._2.getMinPay, d._2.getStored,
                p.paymentMap(d._1).getValue, change, d._2.getEpochsMined, "none", 0L, block.blockheight, LocalDateTime.now())
              poolMembers += poolMember
            }else{
              val poolMember = PoolMember(p.token.toString, p.id, completedGroups(p).getId.replace("\"", ""),
                p.nextBox.getId.toString, pool.globalEpoch + 1, p.nextBox.epoch,
                p.nextBox.epochHeight, d._1.address.toString, d._2.getScore, shareNum, sharePerc, d._2.getMinPay, d._2.getStored,
                0L, change, d._2.getEpochsMined, "none", 0L, block.blockheight, LocalDateTime.now())
              poolMembers += poolMember
            }
        }
    }
    poolMembers
  }

  def getAmountAdded(subpool: Subpool, nextDistValue: (PropBytes, MemberInfo)): Long = {
    val lastInfo  = subpool.box.shareDistribution.dist.find(lastDist => lastDist._1 == nextDistValue._1)
    if(lastInfo.isDefined) {
      if(lastInfo.get._2.getStored > 0) {
        if(nextDistValue._2.getStored > 0)
          nextDistValue._2.getStored - lastInfo.get._2.getStored
        else
          subpool.paymentMap(nextDistValue._1).getValue - lastInfo.get._2.getStored
      }else{
        nextDistValue._2.getStored
      }
    }else{
      if(nextDistValue._2.getStored > 0)
        nextDistValue._2.getStored
      else
        subpool.paymentMap(nextDistValue._1).getValue.toLong
    }

  }

  def getNextStates(block: Block): Array[PoolState] = {
    completedGroups.map{ g =>
      PoolState(g._1.token.toString, g._1.id, "notUpdated", g._1.nextBox.getId.toString, g._2.getId.replace("\"", ""),
        0L, g._1.nextBox.epoch, g._1.nextBox.genesis, g._1.nextBox.epochHeight, PoolState.SUCCESS, g._1.nextBox.shareDistribution.size,
        block.blockheight, "notUpdated", g._1.nextStorage.map(_.getId.toString).getOrElse("none"), g._1.nextStorage.map(_.getValue.toLong).getOrElse(0L),
        LocalDateTime.now(), LocalDateTime.now())
    }.toArray
  }

}