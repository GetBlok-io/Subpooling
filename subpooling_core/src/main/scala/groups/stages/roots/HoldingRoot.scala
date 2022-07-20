package io.getblok.subpooling_core
package groups.stages.roots

import contracts.holding.HoldingContract
import global.{AppParameters, EIP27Constants, Helpers}
import global.AppParameters.{NodeWallet, PK}
import groups.entities.{Pool, Subpool}
import groups.models.TransactionStage

import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox, OutBox}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class HoldingRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, holdingContract: HoldingContract, baseFeeMap: Map[Address, Long],
                  var inputBoxes: Option[Seq[InputBox]] = None, sendTxs: Boolean = true)
  extends TransactionStage[InputBox](pool, ctx, wallet) with ParallelRoot {
  override val stageName: String = "HoldingRoot"
  val log = LoggerFactory.getLogger(stageName)
  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try {
        val totalTxFees = pool.subPools.size * AppParameters.groupFee
        val totalBaseFees = baseFeeMap.values.sum
        val totalOutputs = pool.subPools.map(p => p.nextHoldingValue).sum
        log.info(s"Pool size: ${pool.subPools.size}")
        log.info(s"Total Tx fees: $totalTxFees, Total Base fees: $totalBaseFees, Total outputs: $totalOutputs")
        var initialInputs = inputBoxes
        log.info("Re-checking input boxes for correct values")
        // Paranoid checks, root transaction is handed off maximum amount of emission currency for the group
        // In rare cases, this may lead to unexpected selected boxes due to difference in real subpool selection vs
        // max selection

        // TODO: Parameterize paranoid checks so they may be set from config
        if(inputBoxes.isDefined) {
          initialInputs = Some(Seq())
          val totalAmountNeeded = totalTxFees + totalBaseFees + totalOutputs
          var sortedInputs = mutable.Queue(inputBoxes.get.sortBy(i => i.getValue.toLong).reverse:_*)
          log.info(s"Total amount needed for tx: ${totalAmountNeeded}")
          log.info(s"Total amount of inputs ${sortedInputs.toSeq.map(_.getValue.toLong).sum}")
          log.info(s"Total number of inputs ${sortedInputs.length}")
          var initialSum: Long = 0L
          val totalInputSum = inputBoxes.get.map(_.getValue.toLong).sum
          log.info("Now pruning input boxes")
          if(totalInputSum > totalAmountNeeded) {
            while (initialSum < totalAmountNeeded) {
              val input = sortedInputs.dequeue()
              log.info(s"Adding input box with id ${input.getId} and value ${Helpers.nanoErgToErg(input.getValue)} ERG")
              initialSum = initialSum + input.getValue
              if(input.getTokens.size() > 0){
                if(input.getTokens.get(0).getId == EIP27Constants.REEM_TOKEN){
                  initialSum = initialSum - input.getTokens.get(0).getValue
                  log.info(s"Subtracted ${Helpers.nanoErgToErg(input.getTokens.get(0).getValue)} ERG to conform to EIP-27 rules")
                }
              }
              initialInputs = Some(initialInputs.get ++ Seq(input))
            }
          }else{
            log.info("Total inputs not greater than amount needed!")
            throw new Exception("Not enough inputs")
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

        val boxesToSpend = initialInputs.getOrElse(wallet.boxes(ctx, totalTxFees + totalBaseFees + totalOutputs).get.asScala.toSeq)
        val eip27 = EIP27Constants.applyEIP27(ctx.newTxBuilder(), boxesToSpend)

        boxesToSpend.foreach(i => log.info(s"Id: ${i.getId}, val: ${i.getValue}"))

        val txB = ctx.newTxBuilder()
        val outputBoxes = outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1)
        outputBoxes.foreach(o => log.info(s"Output value: ${o.getValue}"))

        val unsignedTx = {
          if(eip27.optToBurn.isDefined){
            txB
              .boxesToSpend(boxesToSpend.asJava)
              .fee(totalTxFees)
              .outputs((outputBoxes ++ feeOutputs ++ eip27.p2reem): _*)
              .sendChangeTo(wallet.p2pk.getErgoAddress)
              .tokensToBurn(eip27.optToBurn.get)
              .build()
          }else {
            txB
              .boxesToSpend(boxesToSpend.asJava)
              .fee(totalTxFees)
              .outputs((outputBoxes ++ feeOutputs): _*)
              .sendChangeTo(wallet.p2pk.getErgoAddress)
              .build()
          }
        }

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = {
          if(sendTxs)
            ctx.sendTransaction(transaction.get)
          else
            transaction.get.getId.replace("\"", "")
        }
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
