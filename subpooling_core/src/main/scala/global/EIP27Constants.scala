package io.getblok.subpooling_core
package global

import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox, OutBox, UnsignedTransactionBuilder}

object EIP27Constants {
  val REEM_TOKEN: ErgoId = ErgoId.create("d9a2cc8a09abfaed87afacfbb7daee79a6b26f10c6613fc13d3f3953e5521d1a")
  val PAY_TO_REEM_ADDRESS: Address = Address.create("6KxusedL87PBibr1t1f4ggzAyTAmWEPqSpqXbkdoybNwHVw5Nb7cUESBmQw5XK8TyvbQiueyqkR9XMNaUgpWx3jT54p")
  case class EIP27Results(p2reem: Seq[OutBox], optToBurn: Option[ErgoToken])

  def applyEIP27(txB: UnsignedTransactionBuilder, boxesToSpend: Seq[InputBox]): EIP27Results = {
    val reemTokens = boxesToSpend.filter(i => i.getTokens.size() > 0)
      .filter(i => i.getTokens.get(0).getId == REEM_TOKEN)
      .map(i => i.getTokens.get(0).getValue).sum

    if(reemTokens < 0){
      EIP27Results(Seq.empty[OutBox], None)
    }else{
      val outBox = {
        txB.outBoxBuilder()
          .value(reemTokens)
          .contract(PAY_TO_REEM_ADDRESS.toErgoContract)
          .build()
      }
      val tokensToBurn = Some(new ErgoToken(REEM_TOKEN, reemTokens))
      EIP27Results(Seq(outBox), tokensToBurn)
    }
  }
}
