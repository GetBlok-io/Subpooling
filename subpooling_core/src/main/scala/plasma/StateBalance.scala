package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.{Ints, Longs}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import sigmastate.eval.Colls
import special.collection.Coll

case class StateScore(score: Long, paid: Boolean) {

  val ergoType: ErgoType[Byte] = ErgoType.byteType()
  def toErgoValue: ErgoValue[Coll[Byte]] = ErgoValue.of(Colls.fromArray(toBytes), ErgoType.byteType())
  def toBytes: Array[Byte] = Longs.toByteArray(score) ++ Array(if(paid) 1.toByte else 0.toByte)
  def toColl: Coll[Byte] = Colls.fromArray(toBytes)
  override def toString: String = s"StScore[${score}](${Hex.toHexString(toBytes)})"
}
