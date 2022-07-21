package io.getblok.subpooling_core
package states.transforms

import contracts.plasma.{InsertBalanceContract, UpdateBalanceContract}
import global.AppParameters.NodeWallet
import states.models.{CommandState, CommandTypes, State, StateTransition, TransformResult}

import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.SingleBalance
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, minerConversion}
import org.ergoplatform.appkit.BlockchainContext
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try


case class UpdateTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState)
                          extends StateTransition[SingleBalance](ctx, wallet, commandState){
  private val logger: Logger = LoggerFactory.getLogger("UpdateTransform")
  override def transform(state: State[SingleBalance]): Try[TransformResult[SingleBalance]] = {
    Try{
      val allPositive = commandState.data.forall(_.amountAdded > 0)
      require(allPositive, "Not all updates were positive!")
      require(commandState.box.getValue >= commandState.data.map(_.amountAdded).sum, s"UpdateBox with value ${commandState.box.getValue} was not" +
        s" big enough for command state with total added value of ${commandState.data.map(_.amountAdded).sum}")
      val appliedCommand = UpdateBalanceContract.applyContext(commandState.box, state.balanceState, commandState.data.map(_.toUpdateStateValues))

      logger.info(s"Update transform is adding ${appliedCommand._2} accumulated balance!")
      logger.info(s"Paying transaction fee of ${appliedCommand._1.getValue - appliedCommand._2}")
      require(appliedCommand._1.getValue - appliedCommand._2 <= Helpers.OneErg, "A tx fee greater than 1 erg is being paid!")
      val inputBoxes = Seq(state.box, appliedCommand._1).asJava
      val nextStateBox = state.output(ctx, wallet.p2pk, Some(state.box.getValue.toLong + appliedCommand._2))
      val unsignedTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs(nextStateBox)
        .fee(appliedCommand._1.getValue - appliedCommand._2)
        .sendChangeTo(wallet.p2pk.getErgoAddress)
        .build()

      val signedTx = wallet.prover.sign(unsignedTx)
      val nextInputState = nextStateBox.convertToInputWith(signedTx.getId.replace("\"", ""), 0)
      val nextState = state.copy(box = nextInputState)
      val manifest = state.balanceState.map.toPlasmaMap.getManifest(255)
      TransformResult(nextState, signedTx, commandState.data, CommandTypes.UPDATE, Some(manifest), commandState.index, commandState)
    }
  }

}
