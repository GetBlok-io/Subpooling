package io.getblok.subpooling_core
package states.transforms

import contracts.plasma.InsertBalanceContract
import global.AppParameters.NodeWallet
import plasma.StateBalance
import states.models._

import io.getblok.getblok_plasma.ByteConversion
import io.getblok.subpooling_core.plasma.StateConversions.minerConversion
import org.ergoplatform.appkit.BlockchainContext
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try

case class InsertTransform[T <: StateBalance](override val ctx: BlockchainContext, override val wallet: NodeWallet,
                                              override val commandState: CommandState)(implicit convT: ByteConversion[T])
  extends StateTransition[T](ctx, wallet, commandState) {

  override def transform(state: State[T]): Try[TransformResult[T]] = {
    Try {

      val allNewMiners = state.balanceState.map.lookUp(commandState.data.map(_.toStateMiner.toPartialStateMiner): _*)
      require(allNewMiners.response.forall(m => m.tryOp.get.isEmpty), "A given miner already exists in the state!")

      val appliedCommand = InsertBalanceContract.applyContext(commandState.box, state.balanceState, commandState.data.map(_.toStateMiner.toPartialStateMiner),
        state.zeroed)
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
      val nextState = state.copyState(_box = nextInputState)
      val logger = LoggerFactory.getLogger("InsertTransform")

      val manifest = state.balanceState.map.getTempMap.get.getManifest(255)
      TransformResult(nextState, signedTx, commandState.data, CommandTypes.INSERT, Some(manifest), commandState.index, commandState)
    }
  }

}
