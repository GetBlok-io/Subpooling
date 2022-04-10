package io.getblok.subpooling_core
package transactions

import transactions.models.TransactionTemplate

import org.ergoplatform.appkit._

class GenesisTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends TransactionTemplate(unsignedTxBuilder){

  private[this] var _creatorAddress: Address = _
  private[this] var _metadataValue: Long = 0L
  private[this] var _txFee: Long = 0L
  private[this] var _metadataContract: ErgoContract = _
  private[this] var _smartPoolNFT: ErgoToken = _
  private[this] var _tokenInputBox: InputBox = _

  def tokenInputBox: InputBox = _tokenInputBox

  def tokenInputBox(value: InputBox): GenesisTx = {
    _tokenInputBox = value
    this
  }

  def smartPoolNFT: ErgoToken = _smartPoolNFT

  def smartPoolNFT(value: ErgoToken): GenesisTx = {
    _smartPoolNFT = value
    this
  }

  def creatorAddress: Address = _creatorAddress

  def creatorAddress(value: Address): GenesisTx = {
    _creatorAddress = value
    this
  }

  def metadataValue: Long = _metadataValue

  def metadataValue(value: Long): GenesisTx = {
    _metadataValue = value
    this
  }

  def txFee: Long = _txFee

  def txFee(value: Long): GenesisTx = {
    _txFee = value
    this
  }

  def metadataContract: ErgoContract = _metadataContract

  def metadataContract(contract: ErgoContract): GenesisTx = {
    _metadataContract = contract
    this
  }

  override def build(): UnsignedTransaction = {

//    val genesisBox: OutBox = MetadataContract.buildGenesisBox(new MetadataOutputBuilder(this.outBoxBuilder()), metadataContract, creatorAddress, metadataValue, ctx.getHeight, smartPoolNFT, 0)
//
//    val unsignedTx = asUnsignedTxB
//      .boxesToSpend(List[InputBox](tokenInputBox).asJava)
//      .fee(txFee)
//      .sendChangeTo(_creatorAddress.getErgoAddress)
//      .outputs(genesisBox)
//      .build()
    unsignedTxBuilder.build()
  }
}
