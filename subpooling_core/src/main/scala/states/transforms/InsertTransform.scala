package io.getblok.subpooling_core
package states.transforms

import states.models.{CommandState, CommandTypes, State, StateTransition, TransformResult}

import io.getblok.subpooling_core.contracts.plasma.{BalanceStateContract, InsertBalanceContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, minerConversion}
import org.ergoplatform.appkit.{BlockchainContext, ErgoContract, SignedTransaction}

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try



case class InsertTransform(override val ctx: BlockchainContext, override val wallet: NodeWallet, override val commandState: CommandState)
                          extends StateTransition(ctx, wallet, commandState){

  override def transform(state: State): Try[TransformResult] = {
    Try{
      val allNewMiners = state.balanceState.map.lookUp(commandState.data.map(_.toStateMiner.toPartialStateMiner): _*)
      require(allNewMiners.response.forall(m => m.tryOp.get.isEmpty), "A given miner already exists in the state!")

      val appliedCommand = InsertBalanceContract.applyContext(commandState.box, state.balanceState, commandState.data.map(_.toStateMiner.toPartialStateMiner))
      val inputBoxes = Seq(state.box, appliedCommand).asJava
      val nextStateBox = state.output(ctx)
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
      TransformResult(nextState, signedTx, commandState.data, CommandTypes.INSERT, Some(manifest))
    }
  }

}
