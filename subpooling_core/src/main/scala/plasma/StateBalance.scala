package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.Longs
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import sigmastate.eval.Colls
import special.collection.Coll

trait StateBalance {
  val ergoType: ErgoType[java.lang.Byte] = ErgoType.byteType()
  def toErgoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(toColl, ErgoType.byteType())
  def toBytes: Array[Byte]
  def toColl: Coll[java.lang.Byte] = Colls.fromArray(toBytes).asInstanceOf[Coll[java.lang.Byte]]

}
