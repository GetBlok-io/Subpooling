package io.getblok.subpooling_core
package transactions

import transactions.models.TransactionTemplate

import io.getblok.subpooling_core.global.AppParameters
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract

import scala.collection.JavaConverters.seqAsJavaListConverter

class CreateSubpoolTokenTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends TransactionTemplate(unsignedTxBuilder){
  private[this] var _inputBoxes: Seq[InputBox] = Seq.empty[InputBox]

 def inputBoxes: Seq[InputBox] = _inputBoxes

 def inputBoxes(value: Seq[InputBox]): CreateSubpoolTokenTx = {
    _inputBoxes = value
    this
  }

  private[this] var _numSubpools: Long = 0L

  def numSubpools: Long = _numSubpools

  def numSubpools(value: Long): CreateSubpoolTokenTx = {
    _numSubpools = value
    this
  }

  private[this] var _txFee: Long = 0L

  def txFee: Long = _txFee

  def txFee(value: Long): CreateSubpoolTokenTx = {
    _txFee = value
    this
  }

  private[this] var _metadataValue: Long = 0L

  def metadataValue: Long = _metadataValue

  def metadataValue(value: Long): CreateSubpoolTokenTx = {
    _metadataValue = value
    this
  }

  private[this] var _creatorAddress: Address = _

  def creatorAddress: Address = _creatorAddress

  def creatorAddress(value: Address): CreateSubpoolTokenTx = {
    _creatorAddress = value
    this
  }


  override def build(): UnsignedTransaction = {

    val subpoolTokens = new Eip4Token(_inputBoxes.head.getId.toString, numSubpools, "GetBlok.io Subpool Token", "This token identifier represents a subpool under GetBlok.io's Subpooling system.", 0)
    val outBox = unsignedTxBuilder.outBoxBuilder()
      .value(numSubpools * metadataValue + txFee)
      .mintToken(subpoolTokens)
      .contract(new ErgoTreeContract(_creatorAddress.getErgoAddress.script, AppParameters.networkType))
      .build()

    val unsignedTx = asUnsignedTxB
      .boxesToSpend(_inputBoxes.toList.asJava)
      .fee(txFee)
      .sendChangeTo(_creatorAddress.getErgoAddress)
      .outputs(outBox)
      .build()
    unsignedTx
  }
}
