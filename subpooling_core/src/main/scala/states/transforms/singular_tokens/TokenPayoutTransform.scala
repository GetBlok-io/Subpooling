package io.getblok.subpooling_core
package states.transforms.singular_tokens

import contracts.plasma.PayoutBalanceContract
import global.AppParameters.NodeWallet
import global.Helpers
import states.models._

import io.getblok.subpooling_core.plasma.SingleBalance
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, minerConversion}
import org.ergoplatform.appkit.{BlockchainContext, ErgoToken}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try

case class TokenPayoutTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState)
  extends StateTransition[SingleBalance](ctx, wallet, commandState) {
  private val logger: Logger = LoggerFactory.getLogger("PayoutTransform")

  override def transform(inputState: State[SingleBalance]): Try[TransformResult[SingleBalance]] = {
    Try {
      val state = inputState.asInstanceOf[TokenState]
      val currentBalances = state.balanceState.map.lookUp(commandState.data.map(_.toStateMiner.toPartialStateMiner): _*).response
      val allSignificant = currentBalances.forall(_.tryOp.get.get.balance > Helpers.MinFee)

      require(allSignificant, "Not all payouts were greater than 0.001 ERG!")
      require(currentBalances.forall(_.tryOp.get.isDefined), "Not all miners existed in the balance state!")

      val totalRemoved = currentBalances.map(_.tryOp.get.get.balance).sum

      logger.info(s"Paying out a total of ${totalRemoved} to ${currentBalances.length} miners!")

      require(state.box.getTokens.get(1).getValue >= totalRemoved, s"StateBox with tokens ${state.box.getTokens.get(1).getValue} was not" +
        s" big enough for payouts totalling in ${totalRemoved} tokens ")
      val appliedCommand = PayoutBalanceContract.applyContext(commandState.box, state.balanceState, commandState.data.map(_.toStateMiner), state.zeroed)

      logger.info(s"Paying transaction fee of ${commandState.box.getValue} nanoERG")
      require(commandState.box.getValue <= Helpers.OneErg, "A tx fee greater than 1 erg is being paid!")
      val inputBoxes = Seq(state.box, appliedCommand._1).asJava
      val nextStateBox = state.output(ctx, wallet.p2pk, Some(state.box.getValue),
        Some(
          new ErgoToken(state.tokenId, state.box.getTokens.get(1).getValue - totalRemoved)
        )
      )
      val payoutBoxes = PayoutBalanceContract.buildTokenPaymentBoxes(ctx, state.tokenId, appliedCommand._2)
      val outputs = Seq(nextStateBox) ++ payoutBoxes
      val unsignedTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs(outputs: _*)
        .fee(commandState.box.getValue - (commandState.data.length * Helpers.MinFee))
        .sendChangeTo(wallet.p2pk.getErgoAddress)
        .build()

      val signedTx = wallet.prover.sign(unsignedTx)
      val nextInputState = nextStateBox.convertToInputWith(signedTx.getId.replace("\"", ""), 0)
      val nextState = state.copy(box = nextInputState)
      val manifest = state.balanceState.map.getTempMap.get.getManifest(255)
      TransformResult(nextState, signedTx, commandState.data, CommandTypes.PAYOUT, Some(manifest), commandState.index, commandState)
    }
  }

}
