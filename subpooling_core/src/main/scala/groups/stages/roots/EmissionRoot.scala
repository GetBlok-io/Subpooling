package io.getblok.subpooling_core
package groups.stages.roots

import contracts.holding.HoldingContract
import global.AppParameters
import global.AppParameters.{NodeWallet, PK}
import groups.entities.{Pool, Subpool}
import groups.models.TransactionStage

import io.getblok.subpooling_core.boxes.EmissionsBox
import org.ergoplatform.appkit.BoxOperations.ExplorerApiUnspentLoader
import org.ergoplatform.appkit.{Address, BlockchainContext, BoxOperations, ErgoId, ErgoToken, InputBox, OutBox, Parameters}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class EmissionRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, holdingContract: HoldingContract, blockReward: Long, baseFeeMap: Map[Address, Long],
                   emissionsBox: EmissionsBox, var inputBoxes: Option[Seq[InputBox]] = None, sendTxs: Boolean = true)
  extends TransactionStage[InputBox](pool, ctx, wallet) with ParallelRoot {
  override val stageName: String = "EmissionRoot"
  val logger: Logger = LoggerFactory.getLogger(stageName)
  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try {
        val totalTxFees = pool.subPools.size * AppParameters.groupFee
        val totalBaseFees = baseFeeMap.values.sum
        val totalOutputTokens = pool.subPools.size * pool.subPools.map(p => p.nextHoldingValue).sum
        val totalOutputErg    = pool.subPools.size * Parameters.MinFee * 10
        logger.info(s"Pool size: ${pool.subPools.size}")
        logger.info(s"Block reward: $blockReward")
        logger.info(s"Total Tx fees: $totalTxFees, Total Base fees: $totalBaseFees, totalOutputErg: $totalOutputErg, Total outputs tokens: $totalOutputTokens")

        var outputMap = Map.empty[Subpool, (OutBox, Int)]
        var outputIndex: Int = 2
        for (subPool <- pool.subPools) {

          val outB = ctx.newTxBuilder().outBoxBuilder()

          val outBox = outB
            .contract(holdingContract.asErgoContract)
            .value(Parameters.MinFee * 10)
            .tokens(new ErgoToken(emissionsBox.tokenId, subPool.nextHoldingValue))
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

        val emissionCycle = emissionsBox.contract.cycleEmissions(ctx, emissionsBox, blockReward - totalBaseFees - totalTxFees - totalOutputErg)

        val boxesToSpend = inputBoxes.getOrElse(wallet.boxes(ctx, totalTxFees + totalBaseFees + blockReward).get.asScala.toSeq)


        boxesToSpend.foreach(i => logger.info(s"Id: ${i.getId}, val: ${i.getValue}"))

        val txB = ctx.newTxBuilder()
        val outputBoxes = outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1)
        outputBoxes.foreach(o => logger.info(s"Output value: ${o.getValue}"))

        val unsignedTx = txB
          .boxesToSpend((Seq(emissionsBox.asInput) ++ boxesToSpend).asJava)
          .fee(totalTxFees)
          .outputs((emissionCycle ++ outputBoxes ++ feeOutputs): _*)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = if(sendTxs) {
          ctx.sendTransaction(transaction.get).replace("\"", "")
        }else{
          transaction.get.getId.replace("\"", "")
        }
        logger.info(txId)
        val inputMap: Map[Subpool, InputBox] = outputMap.map(om => om._1 -> om._2._1.convertToInputWith(txId.replace("\"", ""), om._2._2.toShort))
        inputMap
      }
    }

    this
  }

  /**
   * Predicts total ERG value of Input boxes required to "fuel" the entire group through its phases(stages / chains)
   *
   * @return
   */
  def predictTotalInputs: Long = {
    val totalTxFees = pool.subPools.size * AppParameters.groupFee
    val totalBaseFees = baseFeeMap.values.sum
    val totalOutputs = blockReward
    totalTxFees + totalBaseFees + totalOutputs
  }
}

object EmissionRoot {

  def getMaxInputs(blockReward: Long): Long = blockReward
}


