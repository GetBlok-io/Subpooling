package io.getblok.subpooling_core
package states.transforms

import contracts.plasma.{InsertBalanceContract, PayoutBalanceContract, UpdateBalanceContract}
import global.AppParameters.NodeWallet
import global.{AppParameters, Helpers}
import states.models.{CommandState, State, StateTransition, TransformResult}

import io.getblok.subpooling_core.states.models.CommandTypes.{INSERT, PAYOUT, SETUP, UPDATE}
import org.ergoplatform.appkit.BlockchainContext
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try


case class SetupTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState,
                          minerBatchSize: Int)
  extends StateTransition(ctx, wallet, commandState){
  private val logger: Logger = LoggerFactory.getLogger("SetupTransform")

  var commandQueue: IndexedSeq[CommandState] = _
  override def transform(state: State): Try[TransformResult] = {
    Try{
      val minerLookupResults = commandState.data.zip(
        state.balanceState.map.lookUp(commandState.data.map(_.toStateMiner.toPartialStateMiner): _*).response
      )

      val newMiners = minerLookupResults.filter(_._2.tryOp.get.isEmpty).map(_._1)
      require(commandState.data.forall(_.amountAdded > 0L), "Not all miners made a contribution!")
      require(newMiners.forall(_.balance == 0L), "Not all new miners had a balance of 0!")

      val minersToPayout = minerLookupResults.filter(m => m._1.balance + m._1.amountAdded >= m._1.minPay).map(_._1)

      val insertBatches = newMiners.sliding(minerBatchSize, minerBatchSize).toSeq
      val payoutBatches = minersToPayout.sliding(minerBatchSize, minerBatchSize).toSeq
      val updateBatches = commandState.data.sliding(minerBatchSize, minerBatchSize).toSeq


      val insertOutBoxes = insertBatches.indices.map{
        idx =>
          InsertBalanceContract.buildBox(ctx, Some(AppParameters.groupFee * 20)) -> idx
      }
      val updateOutBoxes = updateBatches.indices.map{
        idx =>
          val amountToPayout = updateBatches(idx).map(_.amountAdded).sum
          logger.info(s"Building update box with total balances of ${amountToPayout} nanoErg")
          UpdateBalanceContract.buildBox(ctx, Some(amountToPayout + (AppParameters.groupFee * 20))) -> (insertBatches.size + idx)
      }
      val payoutOutBoxes = payoutBatches.indices.map{
        idx =>
          PayoutBalanceContract.buildBox(ctx, Some(AppParameters.groupFee * 20)) -> (insertBatches.size + updateBatches.size + idx)
      }

      val indexedOutputs = insertOutBoxes ++ updateOutBoxes ++ payoutOutBoxes
      val outputs = indexedOutputs.map(_._1)
      logger.info(s"Paying transaction fee of ${commandState.box.getValue} nanoERG")
      val txFee = AppParameters.groupFee * 20
      require(state.boxes.map(_.getValue).sum > outputs.map(_.getValue).sum + txFee, "Input value was not big enough for required outputs!")

      val inputBoxes = state.boxes.asJava

      val unsignedTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs(outputs: _*)
        .fee(txFee)
        .sendChangeTo(wallet.p2pk.getErgoAddress)
        .build()

      val signedTx = wallet.prover.sign(unsignedTx)
      val txId = signedTx.getId.replace("\"", "")

      val insertCommands = insertOutBoxes
        .map(o => o._1.convertToInputWith(txId, o._2.toShort))
        .zipWithIndex
        .map(i => CommandState(i._1, insertBatches(i._2), INSERT, i._2))

      val updateCommands = updateOutBoxes
        .map(o => o._1.convertToInputWith(txId, o._2.toShort))
        .zipWithIndex
        .map(i => CommandState(i._1, updateBatches(i._2), UPDATE, insertCommands.length + i._2))

      val payoutCommands = payoutOutBoxes
        .map(o => o._1.convertToInputWith(txId, o._2.toShort))
        .zipWithIndex
        .map(i => CommandState(i._1, payoutBatches(i._2), PAYOUT, insertCommands.length + updateCommands.length + i._2))

      commandQueue = insertCommands ++ updateCommands ++ payoutCommands
      TransformResult(state, signedTx, commandState.data, SETUP, None, -1, commandState)
    }
  }

}
