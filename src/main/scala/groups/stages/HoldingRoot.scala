package groups.stages

import app.AppParameters
import app.AppParameters.NodeWallet
import contracts.holding.HoldingContract
import groups.entities.{Pool, Subpool}
import groups.models.TransactionStage
import org.ergoplatform.appkit.{BlockchainContext, InputBox, OutBox}

import scala.util.Try

class HoldingRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, holdingContract: HoldingContract)
  extends TransactionStage[InputBox](pool, ctx, wallet){
  override val stageName: String = "HoldingRoot"
  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try{
        val totalFees    = pool.subPools.size * AppParameters.groupFee
        val totalOutputs = pool.subPools.size * pool.subPools.map(p => p.nextHoldingValue).sum


        var outputMap    = Map.empty[Subpool, (OutBox, Int)]
        var outputIndex: Int = 0
        for(subPool <- pool.subPools){

          val outB = ctx.newTxBuilder().outBoxBuilder()

          val outBox = outB
            .contract(holdingContract.asErgoContract)
            .value(subPool.nextHoldingValue)
            .build()

          outputMap = outputMap + (subPool -> (outBox -> outputIndex))
          outputIndex = outputIndex + 1
        }

        val inputBoxes = ctx.getWallet.getUnspentBoxes(totalFees + totalOutputs)
        val txB = ctx.newTxBuilder()

        val unsignedTx = txB
          .boxesToSpend(inputBoxes.get())
          .fee(totalFees)
          .outputs(outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1):_*)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = ctx.sendTransaction(transaction.get)

        val inputMap: Map[Subpool, InputBox] = outputMap.map(om => om._1 -> om._2._1.convertToInputWith(txId, om._2._2.shortValue()))
        inputMap
      }
    }

    this
  }


}
