package io.getblok.subpooling_core
package groups.stages.roots

import global.AppParameters
import global.AppParameters.NodeWallet
import groups.entities.{Pool, Subpool}
import groups.models.TransactionStage

import org.ergoplatform.appkit.{BlockchainContext, InputBox, OutBox}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

class DistributionRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, inputBoxes: Option[Seq[InputBox]] = None, sendTxs: Boolean = true)
  extends TransactionStage[InputBox](pool, ctx, wallet) with ParallelRoot  {
  override val stageName: String = "DistributionRoot"

  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try {
        val totalFees = pool.subPools.size * AppParameters.groupFee
        val totalOutputs = pool.subPools.size * (AppParameters.commandValue + AppParameters.groupFee)

        // TODO: Possibly use subpool id if reference issues arise
        var outputMap = Map.empty[Subpool, (OutBox, Int)]
        var outputIndex: Int = 0
        for (subPool <- pool.subPools) {

          val outB = ctx.newTxBuilder().outBoxBuilder()

          val outBox = outB
            .contract(wallet.contract)
            .value(AppParameters.commandValue + AppParameters.groupFee)
            .build()

          outputMap = outputMap + (subPool -> (outBox -> outputIndex))
          outputIndex = outputIndex + 1
        }

        val boxesToSpend = inputBoxes.getOrElse(ctx.getWallet.getUnspentBoxes(totalFees + totalOutputs).get().asScala.toSeq)
        val txB = ctx.newTxBuilder()

        val unsignedTx = txB
          .boxesToSpend(boxesToSpend.asJava)
          .fee(totalFees)
          .outputs(outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1): _*)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = if(sendTxs) {
          ctx.sendTransaction(transaction.get).replace("\"", "")
        }else{
          transaction.get.getId.replace("\"", "")
        }
        val inputMap: Map[Subpool, InputBox] = outputMap.map(om => om._1 -> om._2._1.convertToInputWith(txId, om._2._2.shortValue()))
        inputMap
      }
    }

    this
  }

  /**
   * Predicts total ERG value of Input boxes required to "fuel" the entire group through its phases(stages / chains)
   * Used when pre-calculating inputs for parallelization of groups
   */
  override def predictTotalInputs: Long = {
    val totalFees = pool.subPools.size * AppParameters.groupFee
    val totalOutputs = pool.subPools.size * (AppParameters.commandValue + AppParameters.groupFee)
    totalFees + totalOutputs
  }
}

object DistributionRoot {
  /** Gets theoretical max value of inputs using max pool size */
  def getMaxInputs: Long = {
    val totalFees = 100 * AppParameters.groupFee
    val totalOutputs = 100 * (AppParameters.commandValue + AppParameters.groupFee)
    totalFees + totalOutputs
  }
}
