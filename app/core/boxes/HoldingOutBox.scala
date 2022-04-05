package io.getblok.subpooling
package core.boxes

import org.ergoplatform.appkit._
import sigmastate.Values

import java.util

/**
 * Wrapper class that wraps output boxes as command boxes
 *
 * @param outBox Out box to wrap as command box
 */
class HoldingOutBox(outBox: OutBox)
                    extends OutBox{
  val asOutBox: OutBox = outBox
  override def toString: String = {
    val asString = s"""
    Holding Output Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG

    """
    asString
  }

  override def getValue: Long = asOutBox.getValue

  override def getCreationHeight: Int = asOutBox.getCreationHeight

  override def getTokens: java.util.List[ErgoToken] = asOutBox.getTokens

  override def convertToInputWith(txId: String, outputIndex: Short): InputBox = asOutBox.convertToInputWith(txId, outputIndex)

  override def getRegisters: util.List[ErgoValue[_]] = asOutBox.getRegisters

  override def getErgoTree: Values.ErgoTree = asOutBox.getErgoTree

  override def getBytesWithNoRef: Array[Byte] = asOutBox.getBytesWithNoRef
}
