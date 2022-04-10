package io.getblok.subpooling_core
package boxes

import boxes.models.InputTemplate
import contracts.command.CommandContract

import org.ergoplatform.appkit._
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer

/**
 * Wrapper class that wraps input boxes as command boxes
 * May have any contract
 * @param inputBox Input box to wrap as command box
 */
class CommandInputBox(inputBox: InputBox, commandContract: CommandContract) extends InputTemplate(inputBox) {
  // Explicitly define command contract so as to ensure input box is correct
  override val contract: CommandContract = commandContract
  assert(asInput.getErgoTree.bytes sameElements contract.getErgoTree.bytes)

  override def toString: String = {
    def serializer = new ErgoTreeSerializer()
    val asString = s"""
    """
    asString
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case box: CommandInputBox => this.getId.equals(box.getId)
      case _ => false
    }
  }

  override def getErgoTree: Values.ErgoTree = contract.getErgoTree

  override def getBytes: Array[Byte] = asInput.getBytes
}
