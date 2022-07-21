package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.Longs
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{ErgoType, ErgoValue, Iso}
import sigmastate.eval.Colls
import special.collection.Coll

trait StateBalance {
  def toBytes: Array[Byte]

  val ergoType: ErgoType[java.lang.Byte] = ErgoType.byteType()
  def toErgoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(Colls.fromArray(toBytes.map(Iso.jbyteToByte.from)), ErgoType.byteType())
  def toColl: Coll[java.lang.Byte] = Colls.fromArray(toBytes).map(Iso.jbyteToByte.from)
}
