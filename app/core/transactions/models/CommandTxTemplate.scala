package io.getblok.subpooling
package core.transactions.models

import core.boxes.builders.CommandOutputBuilder
import core.boxes.{CommandOutBox, MetadataInputBox}
import core.contracts.command.CommandContract

import core.boxes.builders.CommandOutputBuilder
import core.boxes.{CommandOutBox, MetadataInputBox}
import core.contracts.command.CommandContract
import org.ergoplatform.appkit._

abstract class CommandTxTemplate(unsignedTxBuilder: UnsignedTransactionBuilder) extends TransactionTemplate(unsignedTxBuilder){

  protected[this] var _metadataInputBox: MetadataInputBox = _
  protected[this] var _commandOutBox: CommandOutBox = _
  protected[this] var _commandContract: CommandContract = _
  protected[this] var _commandValue: Long = 0L
  protected[this] var _inputBoxes: List[InputBox] = List[InputBox]()

  var cOB: CommandOutputBuilder = new CommandOutputBuilder(this.outBoxBuilder())

  def metadataInputBox: MetadataInputBox = _metadataInputBox

  def commandOutBox: CommandOutBox = _commandOutBox

  def commandContract: CommandContract = _commandContract

  def commandValue: Long = _commandValue


  def buildCommandTx(): UnsignedTransaction


}
