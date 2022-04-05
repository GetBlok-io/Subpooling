package io.getblok.subpooling
package core.boxes

import core.boxes.models.OutputTemplate
import core.registers.MetadataRegisters
import org.ergoplatform.appkit._
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer

import java.util

/**
 * Wrapper class that wraps output boxes as metadata boxes / command boxes
 *
 * @param outBox Out box to wrap as metadata box / command box
 */
class MetadataOutBox(val outBox: OutBox, metadataRegisters: MetadataRegisters, smartPoolId: ErgoId)
                     extends OutputTemplate(outBox, metadataRegisters) {

  def getSmartPoolId: ErgoId = this.smartPoolId
  override def toString: String = {
    def serializer = new ErgoTreeSerializer()
    val asString = s"""
    Metadata Output Info:
    """
    asString
  }

  override def getRegisters: util.List[ErgoValue[_]] = asOutBox.getRegisters

  override def getErgoTree: Values.ErgoTree = asOutBox.getErgoTree

  override def getBytesWithNoRef: Array[Byte] = asOutBox.getBytesWithNoRef
}
