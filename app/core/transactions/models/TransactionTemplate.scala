package io.getblok.subpooling
package core.transactions.models

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit._

import java.util

abstract class TransactionTemplate(unsignedTxBuilder: UnsignedTransactionBuilder){

  val asUnsignedTxB: UnsignedTransactionBuilder = unsignedTxBuilder
  val ctx: BlockchainContext = unsignedTxBuilder.getCtx


  protected def preHeader(ph: PreHeader): UnsignedTransactionBuilder = asUnsignedTxB.preHeader(ph)

  protected def boxesToSpend(boxes: util.List[InputBox]): UnsignedTransactionBuilder = asUnsignedTxB.boxesToSpend(boxes)

  protected def withDataInputs(boxes: util.List[InputBox]): UnsignedTransactionBuilder = asUnsignedTxB.withDataInputs(boxes)

  protected def outputs(outputs: OutBox*): UnsignedTransactionBuilder = asUnsignedTxB.outputs(outputs:_*)

  protected def fee(feeAmount: Long): UnsignedTransactionBuilder = asUnsignedTxB.fee(feeAmount)

  protected def tokensToBurn(tokens: ErgoToken*): UnsignedTransactionBuilder = asUnsignedTxB.tokensToBurn(tokens:_*)

  protected def sendChangeTo(address: ErgoAddress): UnsignedTransactionBuilder = asUnsignedTxB.sendChangeTo(address)

  protected def build(): UnsignedTransaction = asUnsignedTxB.build()

  protected def getCtx: BlockchainContext = asUnsignedTxB.getCtx

  protected def getPreHeader: PreHeader = asUnsignedTxB.getPreHeader

  protected def getNetworkType: NetworkType = asUnsignedTxB.getNetworkType

  protected def outBoxBuilder(): OutBoxBuilder = asUnsignedTxB.outBoxBuilder()

  protected def getInputBoxes: util.List[InputBox] = asUnsignedTxB.getInputBoxes
}
