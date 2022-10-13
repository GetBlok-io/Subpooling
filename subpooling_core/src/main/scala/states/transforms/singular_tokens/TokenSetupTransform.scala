package io.getblok.subpooling_core
package states.transforms.singular_tokens

import contracts.plasma.{InsertBalanceContract, PayoutBalanceContract, PlasmaScripts, UpdateBalanceContract}
import global.AppParameters.NodeWallet
import global.{AppParameters, EIP27Constants}
import registers.PoolFees
import states.models.CommandTypes.{INSERT, PAYOUT, SETUP, UPDATE}
import states.models._
import global.{AppParameters, EIP27Constants, Helpers}
import states.models.{CommandBatch, CommandState, State, StateTransition, TransformResult}

import io.getblok.subpooling_core.plasma.SingleBalance
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, minerConversion}
import org.ergoplatform.appkit.{BlockchainContext, ErgoToken, InputBox}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

case class TokenSetupTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState,
                               minerBatchSize: Int, holdingBox: InputBox, commandBoxes: Option[CommandBatch] = None)
  extends StateTransition[SingleBalance](ctx, wallet, commandState) {
  private val logger: Logger = LoggerFactory.getLogger("SetupTransform")
  val scriptType: PlasmaScripts.ScriptType = PlasmaScripts.SINGLE_TOKEN
  var commandQueue: IndexedSeq[CommandState] = _

  override def transform(inputState: State[SingleBalance]): Try[TransformResult[SingleBalance]] = {
    Try {
      val state = inputState.asInstanceOf[TokenState]
      val minerLookupResults = commandState.data.zip(
        state.balanceState.map.lookUp(commandState.data.map(_.toStateMiner.toPartialStateMiner): _*).response
      )

      val newMiners = minerLookupResults.filter(_._2.tryOp.get.isEmpty).map(_._1)
      //require(commandState.data.forall(_.amountAdded > 0L), "Not all miners made a contribution!")
      require(newMiners.forall(_.balance == 0L), "Not all new miners had a balance of 0!")

      val minersToPayout = minerLookupResults.filter(m => m._1.balance + m._1.amountAdded >= m._1.minPay).map(_._1)
      val minersToUpdate = commandState.data.filter(_.amountAdded > 0)
      val insertBatches = newMiners.sliding(minerBatchSize, minerBatchSize).toSeq
      val payoutBatches = minersToPayout.sliding(minerBatchSize, minerBatchSize).toSeq
      val updateBatches = minersToUpdate.sliding(minerBatchSize, minerBatchSize).toSeq

      logger.info("Batch summary:")
      logger.info(s"Insert Batches: ${insertBatches.size}")

      logger.info(s"Update Batches: ${updateBatches.size}")

      logger.info(s"Payout Batches: ${payoutBatches.size}")
      val insertOutBoxes = insertBatches.indices.map{
        idx =>
          InsertBalanceContract.buildBox(ctx, state.poolNFT, scriptType, Some(AppParameters.groupFee * 20)) -> idx
      }
      val updateOutBoxes = updateBatches.indices.map {
        idx =>

          val tokensToPayout = updateBatches(idx).map(_.amountAdded).sum
          val updateToken = new ErgoToken(state.tokenId, tokensToPayout)
          logger.info(s"Building update box with total balances of ${tokensToPayout} for token id ${state.tokenId}")
          UpdateBalanceContract.buildBox(ctx, state.poolNFT, scriptType,
            Some(AppParameters.groupFee * 20 ),
            Some(updateToken)
          ) -> (insertBatches.size + idx)
      }

      val payoutOutBoxes = payoutBatches.indices.map {
        idx =>
          PayoutBalanceContract.buildBox(ctx, state.poolNFT, scriptType,
            Some(AppParameters.groupFee * 20 + (updateBatches(idx).length * Helpers.MinFee))
          ) -> (insertBatches.size + updateBatches.size + idx)
      }

      val indexedOutputs = insertOutBoxes ++ updateOutBoxes ++ payoutOutBoxes

      logger.info(s"Paying transaction fee of ${commandState.box.getValue} nanoERG")
      val txFee = AppParameters.groupFee * 20

      var inputBoxes = (state.boxes ++ Seq(holdingBox)).asJava

      val outputs = indexedOutputs.map(_._1)
      val inputSum = state.boxes.map(_.getValue).sum + holdingBox.getValue
      val outputSum = outputs.map(_.getValue).sum + txFee
      require( inputSum >= outputSum , s"Input value ${inputSum} was not big enough for required outputs ${outputSum}")

      val unsignedTx = {
        if (AppParameters.enableEIP27) {
          val eip27 = EIP27Constants.applyEIP27(ctx.newTxBuilder(), inputBoxes.asScala.toSeq)
          if (eip27.optToBurn.isDefined) {
            ctx.newTxBuilder()
              .boxesToSpend(inputBoxes)
              .outputs((outputs ++ eip27.p2reem): _*)
              .fee(txFee)
              .sendChangeTo(wallet.p2pk.getErgoAddress)
              .tokensToBurn(eip27.optToBurn.get)
              .build()
          } else {
            ctx.newTxBuilder()
              .boxesToSpend(inputBoxes)
              .outputs(outputs: _*)
              .fee(txFee)
              .sendChangeTo(wallet.p2pk.getErgoAddress)
              .build()
          }
        } else {
          ctx.newTxBuilder()
            .boxesToSpend(inputBoxes)
            .outputs(outputs: _*)
            .fee(txFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .build()
        }
      }

      val signedTx = wallet.prover.sign(unsignedTx)
      val txId = signedTx.getId.replace("\"", "")

      if(commandBoxes.isEmpty) {
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
        val manifest = state.balanceState.map.getTempMap.get.getManifest(255)
        commandQueue = insertCommands ++ updateCommands ++ payoutCommands
        TransformResult(state, signedTx, commandState.data, SETUP, Some(manifest), -1, commandState)
      }else{
        val insertCommands = commandBoxes.get
          .inserts
          .zipWithIndex
          .map(i => CommandState(i._1, insertBatches(i._2), INSERT, i._2))

        val updateCommands = commandBoxes.get
          .updates
          .zipWithIndex
          .map(i => CommandState(i._1, updateBatches(i._2), UPDATE, insertCommands.length + i._2))

        val payoutCommands = commandBoxes.get
          .payouts
          .zipWithIndex
          .map(i => CommandState(i._1, payoutBatches(i._2), PAYOUT, insertCommands.length + updateCommands.length + i._2))
        val manifest = state.balanceState.map.getTempMap.get.getManifest(255)
        commandQueue = (insertCommands ++ updateCommands ++ payoutCommands).toIndexedSeq
        TransformResult(state, signedTx, commandState.data, SETUP, Some(manifest), -1, commandState)
      }
    }
  }

}
