package io.getblok.subpooling_core
package groups.stages.roots

import contracts.holding.HoldingContract
import global.AppParameters
import global.AppParameters.{NodeWallet, PK}
import groups.entities.{Pool, Subpool}
import groups.models.TransactionStage

import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox, OutBox}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class HoldingRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, holdingContract: HoldingContract, baseFeeMap: Map[Address, Long],
                  var inputBoxes: Option[Seq[InputBox]] = None)
  extends TransactionStage[InputBox](pool, ctx, wallet) with ParallelRoot {
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
        var initialInputs = inputBoxes
        log.info("Re-checking input boxes for correct values")
        // Paranoid checks, root transaction is handed off maximum amount of emission currency for the group
        // In rare cases, this may lead to unexpected selected boxes due to difference in real subpool selection vs
        // max selection
        if(inputBoxes.isDefined) {
          initialInputs = Some(Seq())
          val totalAmountNeeded = totalTxFees + totalBaseFees + totalOutputs
          val sortedInputs = inputBoxes.get.sortBy(i => i.getValue.toLong).reverse.toIterator
          log.info(s"Total amount needed for tx: ${totalAmountNeeded}")
          log.info(s"Total amount of inputs ${sortedInputs.toSeq.map(_.getValue.toLong).sum}")
          log.info(s"Total number of inputs ${sortedInputs.length}")
          var initialSum: Long = 0L
          log.info("Now pruning input boxes")
          while(initialSum < totalAmountNeeded){
            if(sortedInputs.hasNext) {
              val nextBox = sortedInputs.next()
              initialInputs = initialInputs.map(_ ++ Seq(nextBox))
              initialSum = initialSum + nextBox.getValue.toLong
              log.info(s"Current sum: ${initialSum}")
            }
          }
        }

        log.info("Checks complete, now building transaction")
        var outputMap = Map.empty[Subpool, (OutBox, Int)]
        var outputIndex: Int = 0
        log.info("Adding subpool outputs")
        for (subPool <- pool.subPools) {

          val outB = ctx.newTxBuilder().outBoxBuilder()

          val outBox = outB
            .contract(holdingContract.asErgoContract)
            .value(subPool.nextHoldingValue)
            .build()

          outputMap = outputMap + (subPool -> (outBox -> outputIndex))
          outputIndex = outputIndex + 1
        }
        log.info(s"Adding fee outputs for baseFeeMap: ${baseFeeMap}")
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

        log.info("Setting boxes to spend")

        val boxesToSpend = initialInputs.getOrElse(ctx.getWallet.getUnspentBoxes(totalTxFees + totalBaseFees + totalOutputs).get().asScala.toSeq)


        boxesToSpend.foreach(i => log.info(s"Id: ${i.getId}, val: ${i.getValue}"))

        val txB = ctx.newTxBuilder()
        val outputBoxes = outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1)
        outputBoxes.foreach(o => log.info(s"Output value: ${o.getValue}"))

        val unsignedTx = txB
          .boxesToSpend(boxesToSpend.asJava)
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

  /**
   * Predicts total ERG value of Input boxes required to "fuel" the entire group through its phases(stages / chains)
   *
   * @return
   */
  def predictTotalInputs: Long = {
    val totalTxFees = pool.subPools.size * AppParameters.groupFee
    val totalBaseFees = baseFeeMap.values.sum
    val totalOutputs = pool.subPools.size * pool.subPools.map(p => p.nextHoldingValue).sum
    totalTxFees + totalBaseFees + totalOutputs
  }
}

object HoldingRoot {
  def getMaxInputs(blockReward: Long): Long = {
    val totalTxFees = 100 * AppParameters.groupFee

    blockReward + totalTxFees
  }
}
