package io.getblok.subpooling_core
package states.transforms

import contracts.plasma.{DeleteBalanceContract, InsertBalanceContract}
import global.AppParameters.NodeWallet
import plasma.StateConversions.{balanceConversion, minerConversion}
import states.models._

import io.getblok.getblok_plasma.ByteConversion
import org.ergoplatform.appkit.BlockchainContext

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try


case class DeleteTransform[T](override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState)
                             (implicit convT: ByteConversion[T])
                              extends StateTransition[T](ctx, wallet, commandState){

  override def transform(state: State[T]): Try[TransformResult[T]] = {
    Try{
      val allNewMiners = state.balanceState.map.lookUp(commandState.data.map(_.toStateMiner.toPartialStateMiner): _*)
      require(allNewMiners.response.forall(m => m.tryOp.get.isDefined), "A given miner does not exist in the balance state!")

      val appliedCommand = DeleteBalanceContract.applySingleContext(commandState.box, state.balanceState, commandState.data.map(_.toStateMiner.toPartialStateMiner))
      val inputBoxes = Seq(state.box, appliedCommand).asJava
      val nextStateBox = state.output(ctx, wallet.p2pk)
      val unsignedTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs(nextStateBox)
        .fee(appliedCommand.getValue)
        .sendChangeTo(wallet.p2pk.getErgoAddress)
        .build()

      val signedTx = wallet.prover.sign(unsignedTx)
      val nextInputState = nextStateBox.convertToInputWith(signedTx.getId.replace("\"", ""), 0)
      val nextState = state.copy(box = nextInputState)
      val manifest = state.balanceState.map.toPlasmaMap.getManifest(255)
      TransformResult(nextState, signedTx, commandState.data, CommandTypes.INSERT, Some(manifest), commandState.index, commandState)
    }
  }

}
