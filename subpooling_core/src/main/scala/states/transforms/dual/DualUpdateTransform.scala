package io.getblok.subpooling_core
package states.transforms.dual

import contracts.plasma.UpdateBalanceContract
import global.AppParameters.NodeWallet
import global.Helpers
import plasma.{DualBalance, SingleBalance}
import plasma.StateConversions.{balanceConversion, dualBalanceConversion, minerConversion}
import states.models._

import org.ergoplatform.appkit.{BlockchainContext, ErgoToken}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try

case class DualUpdateTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState)
  extends StateTransition[DualBalance](ctx, wallet, commandState) {
  private val logger: Logger = LoggerFactory.getLogger("DualUpdateTransform")

  override def transform(inputState: State[DualBalance]): Try[TransformResult[DualBalance]] = {
    Try {
      val state = inputState.asInstanceOf[DualState]
      val allPositive = commandState.data.forall(_.amountAdded > 0)
      val secondPositive = commandState.data.forall(_.addedTwo > 0)
      require(allPositive && secondPositive, "Not all updates were positive!")
      require(commandState.box.getValue >= commandState.data.map(_.amountAdded).sum, s"UpdateBox with value ${commandState.box.getValue} was not" +
        s" big enough for command state with total added value of ${commandState.data.map(_.amountAdded).sum}")
      require(commandState.box.getTokens.get(0).getValue >= commandState.data.map(_.addedTwo).sum, s"UpdateBox with tokens ${commandState.box.getValue} was not" +
        s" big enough for command state with total added token value of ${commandState.data.map(_.addedTwo).sum}")
      val appliedCommand = UpdateBalanceContract.applyDualContext(commandState.box, state.balanceState, commandState.data.map(_.toUpdateDualValues))

      logger.info(s"Update transform is adding ${appliedCommand._2} accumulated balance!")
      logger.info(s"Paying transaction fee of ${appliedCommand._1.getValue - appliedCommand._2._1}")
      require(appliedCommand._1.getValue.toLong - appliedCommand._2._1 <= Helpers.OneErg, "A tx fee greater than 1 erg is being paid!")
      val inputBoxes = Seq(state.box, appliedCommand._1).asJava
      val nextStateBox = state.output(ctx, wallet.p2pk, Some(state.box.getValue.toLong + appliedCommand._2._1),
        Some(state.addToken(appliedCommand._2._2)))

      val unsignedTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs(nextStateBox)
        .fee(appliedCommand._1.getValue - appliedCommand._2._1)
        .sendChangeTo(wallet.p2pk.getErgoAddress)
        .build()

      val signedTx = wallet.prover.sign(unsignedTx)
      val nextInputState = nextStateBox.convertToInputWith(signedTx.getId.replace("\"", ""), 0)
      val nextState = state.copy(box = nextInputState)
      val manifest = state.balanceState.map.getTempMap.get.getManifest(255)
      TransformResult(nextState, signedTx, commandState.data, CommandTypes.UPDATE, Some(manifest), commandState.index, commandState)
    }
  }

}
