package io.getblok.subpooling_core
package states.transforms.dual

import contracts.plasma.{DeleteBalanceContract, InsertBalanceContract, PayoutBalanceContract, PlasmaScripts, UpdateBalanceContract}
import global.AppParameters.NodeWallet
import global.{AppParameters, EIP27Constants}
import plasma.{DualBalance, SingleBalance}
import plasma.StateConversions.{balanceConversion, dualBalanceConversion, minerConversion}
import registers.PoolFees
import states.models.CommandTypes.{DELETE, INSERT, PAYOUT, SETUP, UPDATE}
import states.models._

import org.ergoplatform.appkit.{BlockchainContext, ErgoId, ErgoToken, InputBox}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

case class DualSetupTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState,
                              holdingBox: InputBox, minerBatchSize: Int, commandBatch: Option[CommandBatch] = None)
  extends StateTransition[DualBalance](ctx, wallet, commandState) {
  private val logger: Logger = LoggerFactory.getLogger("SetupTransform")
  val scriptType: PlasmaScripts.ScriptType = PlasmaScripts.DUAL
  var commandQueue: IndexedSeq[CommandState] = _


  override def transform(inputState: State[DualBalance]): Try[TransformResult[DualBalance]] = {
    Try {
      val state = inputState.asInstanceOf[DualState]

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

      val insertOutBoxes = insertBatches.indices.map {
        idx =>
          InsertBalanceContract.buildBox(ctx, state.poolNFT, scriptType, Some(AppParameters.groupFee * 10)) -> idx
      }
      val updateOutBoxes = updateBatches.indices.map {
        idx =>
          val amountToPayout = updateBatches(idx).map(_.amountAdded).sum
          val tokensToPayout = updateBatches(idx).map(_.addedTwo).sum
          val updateToken = new ErgoToken(state.tokenId, tokensToPayout)
          logger.info(s"Building update box with total balances of ${amountToPayout} nanoErg and secondary balances of" +
            s" ${tokensToPayout} for token ${state.tokenId}")
          UpdateBalanceContract.buildBox(ctx, state.poolNFT, scriptType,
            Some(amountToPayout + (AppParameters.groupFee * 10)),
            Some(updateToken)
          ) -> (insertBatches.size + idx)
      }
      val payoutOutBoxes = payoutBatches.indices.map {
        idx =>
          PayoutBalanceContract.buildBox(ctx, state.poolNFT, scriptType, Some(AppParameters.groupFee * 10)) -> (insertBatches.size + updateBatches.size + idx)
      }
//      val deleteOutBoxes = payoutBatches.indices.map {
//        idx =>
//          DeleteBalanceContract.buildBox(ctx, state.poolNFT, scriptType, Some(AppParameters.groupFee * 10)) -> idx
//      }

      val indexedOutputs = insertOutBoxes ++ updateOutBoxes ++ payoutOutBoxes

      logger.info(s"Paying transaction fee of ${commandState.box.getValue} nanoERG")
      val txFee = AppParameters.groupFee * 20

      val inputBoxes = (Seq(holdingBox) ++ state.boxes).asJava


      val outputs = indexedOutputs.map(_._1)
      require(state.boxes.map(_.getValue).sum + holdingBox.getValue > outputs.map(_.getValue).sum + txFee, "Input value was not big enough for required outputs!")

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
      if(commandBatch.isEmpty) {
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
        val manifest = state.balanceState.map.toPlasmaMap.getManifest(255)
        commandQueue = insertCommands ++ updateCommands ++ payoutCommands
        TransformResult(state, signedTx, commandState.data, SETUP, Some(manifest), -1, commandState)
      }else{
        val insertCommands = commandBatch.get
          .inserts
          .zipWithIndex
          .map(i => CommandState(i._1, insertBatches(i._2), INSERT, i._2))

        val updateCommands = commandBatch.get
          .updates
          .zipWithIndex
          .map(i => CommandState(i._1, updateBatches(i._2), UPDATE, insertCommands.length + i._2))

        val payoutCommands = commandBatch.get
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
