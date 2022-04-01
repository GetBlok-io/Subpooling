package group_tests.groups

import app.AppParameters.NodeWallet
import boxes.{CommandInputBox, MetadataInputBox}
import contracts.command.CommandContract
import contracts.holding.HoldingContract
import org.ergoplatform.appkit.{BlockchainContext, InputBox, SignedTransaction}
import registers.PropBytes

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class DistributionGroup(pool: entities.Pool, ctx: BlockchainContext, wallet: NodeWallet,
                        commandContract: CommandContract, holdingContract: HoldingContract,
                        inputBoxes: Array[InputBox]) extends models.TransactionGroup(pool, ctx, wallet, inputBoxes){
  override var completedGroups: Map[entities.Subpool, SignedTransaction] = Map.empty[entities.Subpool, SignedTransaction]
  override var failedGroups:    Map[entities.Subpool, Throwable]         = Map.empty[entities.Subpool, Throwable]
  override val groupName:       String                          = "DistributionGroup"

  override def executeGroup: models.TransactionGroup = {
    val result = stageManager.execute[CommandInputBox](new stages.CommandStage(pool, ctx, wallet, commandContract, holdingContract))
    pool.subPools.foreach{
      p =>
        p.commandBox = result._1(p)
        p.nextDist   = result._1(p).shareDistribution
    }

    val resultSet = chainManager.execute[MetadataInputBox](new chains.DistributionChain(pool, ctx, wallet, holdingContract))

    pool.subPools --= resultSet._2.keys

    pool.subPools.foreach{
      p =>
        p.nextBox = resultSet._1(p)._2
        val paymentMap = p.nextDist.dist.map{
          d => d._1 -> resultSet._1(p)._1.getOutputsToSpend.asScala.toArray.find(i => PropBytes.ofErgoTree(i.getErgoTree)(ctx.getNetworkType) == d._1)
        }
        p.paymentMap = paymentMap.filter(p => p._2.isDefined).map(p => p._1 -> p._2.get)
    }

    completedGroups = resultSet._1.map(p => p._1 -> p._2._1)
    failedGroups    = resultSet._2

    this
  }
}
