package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.Longs
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import sigmastate.eval.Colls
import special.collection.Coll

case class StateBalance(balance: Long) {

  val ergoType: ErgoType[Byte] = ErgoType.byteType()
  def toErgoValue: ErgoValue[Coll[Byte]] = ErgoValue.of(Colls.fromArray(toBytes), ErgoType.byteType())
  def toBytes: Array[Byte] = Longs.toByteArray(balance)
  def toColl: Coll[Byte] = Colls.fromArray(toBytes)
  override def toString: String = s"StBalance[${balance}](${Hex.toHexString(toBytes)})"
}
