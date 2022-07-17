package io.getblok.subpooling_core
package plasma

import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{Address, ErgoType, ErgoValue, Iso}
import scorex.crypto.hash.{Blake2b256, Digest32}
import sigmastate.Values
import sigmastate.eval.Colls
import special.collection.Coll


case class PartialStateMiner(bytes: Array[Byte]) {
  val ergoType: ErgoType[java.lang.Byte] = ErgoType.byteType()
  def toColl: Coll[java.lang.Byte] = Colls.fromArray(bytes).map(Iso.jbyteToByte.from)
  def toErgoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(toColl, ergoType)
  override def toString: String = Hex.toHexString(bytes)
}
