package io.getblok.subpooling
package core.transactions

import core.boxes.{CommandInputBox, MetadataInputBox, MetadataOutBox}
import org.ergoplatform.appkit.{UnsignedTransaction, UnsignedTransactionBuilder}
import core.transactions.models.MetadataTxTemplate

/**
 * A Modification Tx is a transaction that allows smart pool operators the ability to change settings without
 * including any holding boxes.
 * @param unsignedTxBuilder
 */
class ModificationTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends MetadataTxTemplate(unsignedTxBuilder) {

  def metadataInput(value: MetadataInputBox): ModificationTx = {
    this._metadataInputBox = value
    this
  }

  def commandInput(value: CommandInputBox): ModificationTx = {
    this._commandInputBox = value
    this
  }

  def metadataOutput(value: MetadataOutBox): ModificationTx = {
    this._metadataOutBox = value
    this
  }

  override def buildMetadataTx(): UnsignedTransaction = {
//
//    val metadataContract = metadataInputBox.getContract
//
//    val initBoxes = List(metadataInputBox.asInput, commandInputBox.asInput)
//    val inputBoxes = initBoxes
//
//    metadataOutput(MetadataContract.buildFromCommandBox(mOB, commandInputBox, metadataContract, metadataInputBox.getValue, metadataInputBox.getSmartPoolId))
//
//
//    val txFee = commandInputBox.getValue
//    val outputBoxes = List(metadataOutBox.asOutBox)
//    outputBoxes.foreach(x => println(x.getValue))
//      this.asUnsignedTxB
//      .boxesToSpend(inputBoxes.asJava)
//      .outputs(outputBoxes:_*)
//      .fee(txFee)
//      .sendChangeTo(commandInputBox.contract.getAddress.getErgoAddress)
//      .build()
  unsignedTxBuilder.build()
  }


}
