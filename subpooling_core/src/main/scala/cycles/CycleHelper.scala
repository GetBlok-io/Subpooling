package io.getblok.subpooling_core
package cycles

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.{EIP27Constants, Helpers}
import org.ergoplatform.appkit.{BlockchainContext, InputBox, SignedTransaction}

import scala.jdk.CollectionConverters.seqAsJavaListConverter

object CycleHelper {

  def reArrange(ctx: BlockchainContext, wallet: NodeWallet, boxes: Seq[InputBox], amount: Long): (InputBox, SignedTransaction) = {
    val outBox = ctx.newTxBuilder().outBoxBuilder()
      .value(amount)
      .contract(wallet.contract)
      .build()

    val eip27 = EIP27Constants.applyEIP27(ctx.newTxBuilder(), boxes)

    val uTx = {
      if(eip27.optToBurn.isDefined){
        ctx.newTxBuilder()
          .boxesToSpend(boxes.asJava)
          .outputs((Seq(outBox) ++ eip27.p2reem): _*)
          .fee(Helpers.MinFee)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .tokensToBurn(eip27.optToBurn.get)
          .build()
      }else{
        ctx.newTxBuilder()
          .boxesToSpend(boxes.asJava)
          .outputs(outBox)
          .fee(Helpers.MinFee)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()
      }
    }

    val signed = wallet.prover.sign(uTx)
    val nextInput = signed.getOutputsToSpend.get(0)

    nextInput -> signed
  }
}
