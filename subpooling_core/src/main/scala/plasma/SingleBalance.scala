package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.collections.OpResult
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{ErgoType, ErgoValue, Iso}
import sigmastate.eval.Colls
import special.collection.Coll

case class SingleBalance(balance: Long) extends StateBalance {

  def toBytes: Array[Byte] = Longs.toByteArray(balance)
  override def toString: String = s"SingleBalance[${balance}](${Hex.toHexString(toBytes)})"
}
