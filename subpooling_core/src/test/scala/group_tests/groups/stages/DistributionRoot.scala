package io.getblok.subpooling_core
package group_tests.groups.stages

import group_tests.groups.models.TransactionStage
import group_tests.groups._
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import org.ergoplatform.appkit.{BlockchainContext, InputBox, OutBox}

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

class DistributionRoot(pool: entities.Pool, ctx: BlockchainContext, wallet: NodeWallet, inputBoxes: Array[InputBox])
  extends models.TransactionStage[InputBox](pool, ctx, wallet){
  override val stageName: String = "DistributionRoot"
  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try{
        val totalFees    = pool.subPools.size * AppParameters.groupFee
        val totalOutputs = pool.subPools.size * (AppParameters.commandValue + AppParameters.groupFee)

        // TODO: Possibly use subpool id if reference issues arise
        var outputMap    = Map.empty[entities.Subpool, (OutBox, Int)]
        var outputIndex: Int = 0
        for(subPool <- pool.subPools){

          val outB = ctx.newTxBuilder().outBoxBuilder()

          val outBox = outB
            .contract(wallet.contract)
            .value(AppParameters.commandValue + AppParameters.groupFee)
            .build()

          outputMap = outputMap + (subPool -> (outBox -> outputIndex))
          outputIndex = outputIndex + 1
        }


        val txB = ctx.newTxBuilder()

        val unsignedTx = txB
          .boxesToSpend(inputBoxes.toSeq.asJava)
          .fee(totalFees)
          .outputs(outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1):_*)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        // val txId = ctx.sendTransaction(signedTx)

        val inputMap: Map[entities.Subpool, InputBox] = outputMap.map(om => om._1 -> om._2._1.convertToInputWith(transaction.get.getId, om._2._2.shortValue()))
        inputMap
      }
    }

    this
  }


}
