package groups

import app.AppParameters.NodeWallet
import boxes.{CommandInputBox, MetadataInputBox}
import contracts.command.CommandContract
import contracts.holding.HoldingContract
import groups.chains.DistributionChain
import groups.entities.{Pool, Subpool}
import groups.models.TransactionGroup
import groups.stages.CommandStage
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}
import registers.PropBytes

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class DistributionGroup(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet,
                        commandContract: CommandContract, holdingContract: HoldingContract) extends TransactionGroup(pool, ctx, wallet){
  override var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  override var failedGroups:    Map[Subpool, Throwable]         = Map.empty[Subpool, Throwable]
  override val groupName:       String                          = "DistributionGroup"

  override def executeGroup: TransactionGroup = {
    val result = stageManager.execute[CommandInputBox](new CommandStage(pool, ctx, wallet, commandContract, holdingContract))
    pool.subPools.foreach{
      p =>
        p.commandBox = result._1(p)
        p.nextDist   = result._1(p).shareDistribution
    }

    val resultSet = chainManager.execute[MetadataInputBox](new DistributionChain(pool, ctx, wallet, holdingContract))

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
