package transactions.models

import boxes.builders.{CommandOutputBuilder, MetadataOutputBuilder}
import boxes.{CommandInputBox, CommandOutBox, MetadataInputBox, MetadataOutBox}
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit._

import java.util

abstract class MetadataTxTemplate(unsignedTxBuilder: UnsignedTransactionBuilder) extends TransactionTemplate(unsignedTxBuilder){

  protected[this] var _metadataInputBox: MetadataInputBox = _
  protected[this] var _metadataOutBox: MetadataOutBox = _

  protected[this] var _commandInputBox: CommandInputBox = _

  val mOB: MetadataOutputBuilder = new MetadataOutputBuilder(this.outBoxBuilder())

  def metadataInputBox: MetadataInputBox = _metadataInputBox


  def commandInputBox: CommandInputBox = _commandInputBox


  def metadataOutBox: MetadataOutBox = _metadataOutBox


  def buildMetadataTx(): UnsignedTransaction


}
