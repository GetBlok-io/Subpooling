package io.getblok.subpooling_core
package groups.stages.roots

import boxes.{EmissionsBox, ExchangeEmissionsBox}
import contracts.holding.HoldingContract
import global.AppParameters
import global.AppParameters.{NodeWallet, PK}
import groups.entities.{Pool, Subpool}
import groups.models.TransactionStage

import io.getblok.subpooling_core.registers.PoolFees
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class ExchangeEmissionsRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, holdingContract: HoldingContract, blockReward: Long, baseFeeMap: Map[Address, Long],
                            emissionsBox: ExchangeEmissionsBox, var inputBoxes: Option[Seq[InputBox]] = None, sendTxs: Boolean = true)
  extends TransactionStage[InputBox](pool, ctx, wallet) with ParallelRoot {
  override val stageName: String = "ExchangeEmissionsRoot"
  val logger: Logger = LoggerFactory.getLogger(stageName)
  var totalTokensDistributed: Long = 0
  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try {
        val totalTxFees = (pool.subPools.size + 1) * AppParameters.groupFee
        val primaryTxFees = (pool.subPools.size) * AppParameters.groupFee
        val totalBaseFees = baseFeeMap.values.sum
        val totalHoldingShare = pool.subPools.map(p => p.nextHoldingShare).sum
        val totalOutputErg    = pool.subPools.size * Parameters.MinFee * 10
        logger.info(s"Pool size: ${pool.subPools.size}")
        logger.info(s"Block reward: $blockReward")
        logger.info(s"Total Tx fees: $totalTxFees, Total Base fees: $totalBaseFees, totalOutputErg: $totalOutputErg, Total holding share: $totalHoldingShare")
        logger.info(s"Primary tx fees: ${primaryTxFees}")
        var initialInputs = inputBoxes
        // Paranoid checks, root transaction is handed off maximum amount of emission currency for the group
        // In rare cases, this may lead to unexpected selected boxes due to difference in real subpool selection vs
        // max selection
        if(inputBoxes.isDefined) {
          initialInputs = Some(Seq())
          val totalAmountNeeded = totalTxFees + blockReward
          val sortedInputs = inputBoxes.get.sortBy(i => i.getValue.toLong).reverse.toIterator

          var initialSum: Long = 0L
          while(initialSum < totalAmountNeeded){
            if(sortedInputs.hasNext) {
              val nextBox = sortedInputs.next()
              initialInputs = initialInputs.map(_ ++ Seq(nextBox))
              initialSum = initialSum + nextBox.getValue.toLong
            }
          }
        }


        val boxesToSpend = initialInputs.getOrElse(ctx.getWallet.getUnspentBoxes(blockReward + primaryTxFees).get().asScala.toSeq)
        val interOutBox = ctx.newTxBuilder().outBoxBuilder().value(blockReward).contract(wallet.contract).build()
        val interFeeOutBox = ctx.newTxBuilder().outBoxBuilder().value(primaryTxFees).contract(wallet.contract).build()
        val unsignedInterTx = ctx.newTxBuilder()
          .boxesToSpend(boxesToSpend.asJava)
          .outputs(interOutBox, interFeeOutBox)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .fee(AppParameters.groupFee)
          .build()

        val signedInterTx = wallet.prover.sign(unsignedInterTx)
        val interBox = interOutBox.convertToInputWith(signedInterTx.getId.replace("\"", ""), 0)
        val interFeeBox = interFeeOutBox.convertToInputWith(signedInterTx.getId.replace("\"", ""), 1)
        logger.info(s"Intermediary Box: ${io.getblok.subpooling_core.global.Helpers.nanoErgToErg(interBox.getValue.toLong)}")
        logger.info(s"Intermediary Fee Box: ${io.getblok.subpooling_core.global.Helpers.nanoErgToErg(interFeeBox.getValue.toLong)}")
        logger.info(inputBoxes.map(_.map(_.getValue.toLong).sum).toString)
        var outputMap = Map.empty[Subpool, (OutBox, Int)]
        var outputIndex: Int = 2
        val emissionCycle = emissionsBox.contract.cycleEmissions(ctx, emissionsBox, interBox.getValue - totalOutputErg - (emissionsBox.poolFee.value * interBox.getValue.toLong) / PoolFees.POOL_FEE_CONST)
        logger.info(s"Total output tokens: ${emissionCycle.tokensForHolding}")
        totalTokensDistributed = emissionCycle.tokensForHolding
        for (subPool <- pool.subPools) {

          val outB = ctx.newTxBuilder().outBoxBuilder()
          val amntDistToken = ((subPool.nextHoldingShare * BigInt(emissionCycle.tokensForHolding)) / totalHoldingShare).toLong
          subPool.nextHoldingValue = amntDistToken
          val outBox = outB
            .contract(holdingContract.asErgoContract)
            .value(Parameters.MinFee * 10)
            .tokens(new ErgoToken(emissionsBox.distTokenId, amntDistToken))
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

        boxesToSpend.foreach(i => logger.info(s"Id: ${i.getId}, val: ${i.getValue}"))

        val txB = ctx.newTxBuilder()
        val outputBoxes = outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1)
        outputBoxes.foreach(o => logger.info(s"Output value: ${o.getValue}"))

        val unsignedTx = txB
          .boxesToSpend((Seq(emissionsBox.asInput, interBox).asJava))
          .fee(primaryTxFees)
          .outputs((emissionCycle.outputs ++ outputBoxes): _*)
          .withDataInputs(Seq(emissionCycle.lpBox).asJava)
          .sendChangeTo(AppParameters.getFeeAddress.getErgoAddress)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = if(sendTxs) {
          ctx.sendTransaction(signedInterTx)
          Thread.sleep(1000)
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

    val totalBaseFees = baseFeeMap.values.sum
    val totalOutputs = blockReward
    totalBaseFees + totalOutputs
  }
}
object ExchangeEmissionsRoot {
  def getMaxInputs(blockReward: Long): Long = blockReward + (101 * AppParameters.groupFee)
}



