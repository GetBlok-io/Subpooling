package boxes

import boxes.models.OutputTemplate
import org.ergoplatform.appkit._
import registers._
import sigmastate.Values
import special.collection.Coll

import java.{lang, util}

/**
 * Wrapper class that wraps output boxes as command boxes
 *
 * @param outBox Out box to wrap as command box
 */
class CommandOutBox(outBox: OutBox, metadataRegisters: MetadataRegisters)
                    extends OutputTemplate(outBox, metadataRegisters){

  override def toString: String = {
    val asString = s"""
    Command Output Info:
    """
    asString
  }

  override def getRegisters: util.List[ErgoValue[_]] = asOutBox.getRegisters

  override def getErgoTree: Values.ErgoTree = asOutBox.getErgoTree

  override def getBytesWithNoRef: Array[Byte] = asOutBox.getBytesWithNoRef
}
