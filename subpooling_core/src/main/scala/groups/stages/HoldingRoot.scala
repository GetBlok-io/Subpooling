package io.getblok.subpooling_core
package groups.stages

import groups.entities.{Pool, Subpool}

import io.getblok.subpooling_core.contracts.holding.HoldingContract
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.{NodeWallet, PK}
import io.getblok.subpooling_core.groups.models.TransactionStage
import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox, OutBox}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class HoldingRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, holdingContract: HoldingContract, baseFeeMap: Map[Address, Long])
  extends TransactionStage[InputBox](pool, ctx, wallet) {
  override val stageName: String = "HoldingRoot"
  val log = LoggerFactory.getLogger(stageName)
  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try {
        val totalTxFees = pool.subPools.size * AppParameters.groupFee
        val totalBaseFees = baseFeeMap.values.sum
        val totalOutputs = pool.subPools.size * pool.subPools.map(p => p.nextHoldingValue).sum
        log.info(s"Pool size: ${pool.subPools.size}")
        log.info(s"Total Tx fees: $totalTxFees, Total Base fees: $totalBaseFees, Total outputs: $totalOutputs")

        var outputMap = Map.empty[Subpool, (OutBox, Int)]
        var outputIndex: Int = 0
        for (subPool <- pool.subPools) {

          val outB = ctx.newTxBuilder().outBoxBuilder()

          val outBox = outB
            .contract(holdingContract.asErgoContract)
            .value(subPool.nextHoldingValue)
            .build()

          outputMap = outputMap + (subPool -> (outBox -> outputIndex))
          outputIndex = outputIndex + 1
        }
        val feeOutputs = ArrayBuffer.empty[OutBox]
        for (fee <- baseFeeMap) {
          val outB = ctx.newTxBuilder().outBoxBuilder()

          val outBox = outB
            .contract(PK(fee._1).contract)
            .value(fee._2)
            .build()

          feeOutputs += outBox
          outputIndex = outputIndex + 1
        }


        val inputBoxes = ctx.getWallet.getUnspentBoxes(totalTxFees + totalBaseFees + totalOutputs)

        if(inputBoxes.isPresent) inputBoxes.get().asScala.toArray.foreach(i => log.info(s"Id: ${i.getId}, val: ${i.getValue}"))

        val txB = ctx.newTxBuilder()
        val outputBoxes = outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1)
        outputBoxes.foreach(o => log.info(s"Output value: ${o.getValue}"))

        val unsignedTx = txB
          .boxesToSpend(inputBoxes.get())
          .fee(totalTxFees)
          .outputs((outputBoxes ++ feeOutputs): _*)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = ctx.sendTransaction(transaction.get)
        log.info(txId)
        val inputMap: Map[Subpool, InputBox] = outputMap.map(om => om._1 -> om._2._1.convertToInputWith(txId.replace("\"", ""), om._2._2.toShort))
        inputMap
      }
    }

    this
  }


}
