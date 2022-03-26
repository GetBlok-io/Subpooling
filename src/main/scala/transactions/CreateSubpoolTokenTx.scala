package transactions

import app.AppParameters
import boxes.builders.MetadataOutputBuilder
import contracts.MetadataContract
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoContract, ErgoToken, InputBox, OutBox, UnsignedTransaction, UnsignedTransactionBuilder}
import transactions.models.TransactionTemplate

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
    val subpoolTokens = new ErgoToken(_inputBoxes.head.getId, numSubpools)
    val outBox = unsignedTxBuilder.outBoxBuilder()
      .value(numSubpools * metadataValue + txFee)
      .mintToken(subpoolTokens, "Subpool Token", "GetBlok.io Subpool Token", 0)
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
