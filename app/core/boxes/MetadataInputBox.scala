package io.getblok.subpooling
package core.boxes

import core.boxes.models.InputTemplate
import org.ergoplatform.appkit._
import sigmastate.serialization.ErgoTreeSerializer

/**
 * Wrapper class that wraps input boxes as metadata boxes / command boxes
 * The metadata input box ensures that token 0 is equal to the smart pool id
 * @param inputBox Input box to wrap as metadata box / command box
 */
class MetadataInputBox(inputBox: InputBox, val subpoolToken: ErgoId) extends InputTemplate(inputBox) {
  if(this.epoch != 0) {
    require(subpoolToken == this.getTokens.get(0).getId)
  } else {
    if(this.getTokens.size() > 0){
      require(subpoolToken == this.getTokens.get(0).getId)
    }else {
      require(subpoolToken == this.getId)
    }
  }

  override def toString: String = {
    val serializer = new ErgoTreeSerializer()
    //val shareConsensusDeserialized = shareConsensus.cValue.map{(sc) => (serializer.deserializeErgoTree(sc._1), sc._2)}
    //val shareConsensusWithAddress = shareConsensusDeserialized.map{(sc) => (Address.fromErgoTree(sc._1, AppParameters.networkType), sc._2)}
    val deserializedOps = this

    val asString = s"""
    MetaData(
    """
    asString
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case box: MetadataInputBox => this.getId.equals(box.getId)
      case _ => false
    }
  }

  override def getBytes: Array[Byte] = asInput.getBytes
}
