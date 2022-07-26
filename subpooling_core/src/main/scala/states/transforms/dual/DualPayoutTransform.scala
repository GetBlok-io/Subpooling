package io.getblok.subpooling_core
package states.transforms.dual

import contracts.plasma.PayoutBalanceContract
import global.AppParameters.NodeWallet
import global.Helpers
import plasma.{DualBalance, SingleBalance}
import plasma.StateConversions.{balanceConversion, dualBalanceConversion, minerConversion}
import states.models._

import org.ergoplatform.appkit.BlockchainContext
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try

case class DualPayoutTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState)
  extends StateTransition[DualBalance](ctx, wallet, commandState) {
  private val logger: Logger = LoggerFactory.getLogger("PayoutTransform")

  override def transform(inputState: State[DualBalance]): Try[TransformResult[DualBalance]] = {
    Try {
      val state = inputState.asInstanceOf[DualState]
      val currentBalances = state.balanceState.map.lookUp(commandState.data.map(_.toStateMiner.toPartialStateMiner): _*).response
      val allSignificant = currentBalances.forall(_.tryOp.get.get.balance > Helpers.MinFee)

      require(allSignificant, "Not all payouts were greater than 0.001 ERG!")
      require(currentBalances.forall(_.tryOp.get.isDefined), "Not all miners existed in the balance state!")

      val totalRemoved = currentBalances.map(_.tryOp.get.get.balance).sum
      val tokensRemoved = currentBalances.map(_.tryOp.get.get.balanceTwo).sum
      logger.info(s"Paying out a total of ${totalRemoved} to ${currentBalances.length} miners!")

      require(state.box.getValue >= totalRemoved, s"StateBox with value ${state.box.getValue} was not" +
        s" big enough for payouts totalling in ${totalRemoved} nanoERG ")
      val appliedCommand = PayoutBalanceContract.applyContext(commandState.box, state.balanceState, commandState.data.map(_.toStateMiner), state.zeroed)

      logger.info(s"Paying transaction fee of ${commandState.box.getValue} nanoERG")
      require(commandState.box.getValue <= Helpers.OneErg, "A tx fee greater than 1 erg is being paid!")
      val inputBoxes = Seq(state.box, appliedCommand._1).asJava
      val nextStateBox = state.output(ctx, wallet.p2pk, Some(state.box.getValue.toLong - totalRemoved),
        Some(state.removeToken(tokensRemoved)))

      val payoutBoxes = PayoutBalanceContract.buildHybridPaymentBoxes(ctx, state.tokenId, appliedCommand._2.toSeq)
      val outputs = Seq(nextStateBox) ++ payoutBoxes
      val unsignedTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs(outputs: _*)
        .fee(commandState.box.getValue)
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
