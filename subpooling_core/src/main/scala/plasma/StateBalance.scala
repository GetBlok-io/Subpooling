package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.Longs
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{ErgoType, ErgoValue, Iso}
import sigmastate.eval.Colls
import special.collection.Coll

case class StateBalance(balance: Long) {

  val ergoType: ErgoType[java.lang.Byte] = ErgoType.byteType()
  def toErgoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(Colls.fromArray(toBytes.map(Iso.jbyteToByte.from)), ErgoType.byteType())
  def toBytes: Array[Byte] = Longs.toByteArray(balance)
  def toColl: Coll[java.lang.Byte] = Colls.fromArray(toBytes.map(Iso.jbyteToByte.from))
  override def toString: String = s"StBalance[${balance}](${Hex.toHexString(toBytes)})"
}
