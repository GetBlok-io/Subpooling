package io.getblok.subpooling_core
package plasma

import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{Address, ErgoType, ErgoValue}
import scorex.crypto.hash.{Blake2b256, Digest32}
import sigmastate.Values
import sigmastate.eval.Colls
import special.collection.Coll


case class PartialStateMiner(bytes: Array[Byte]) {
  val ergoType: ErgoType[Byte] = ErgoType.byteType()
  def toColl: Coll[Byte] = Colls.fromArray(bytes)
  def toErgoValue: ErgoValue[Coll[Byte]] = ErgoValue.of(toColl, ergoType)
  override def toString: String = Hex.toHexString(bytes)
}
