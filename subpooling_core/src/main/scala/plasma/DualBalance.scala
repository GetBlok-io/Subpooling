package io.getblok.subpooling_core
package plasma

import com.google.common.primitives.Longs
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{ErgoType, ErgoValue, Iso}
import sigmastate.eval.Colls
import special.collection.Coll

case class DualBalance(balance: Long, balanceTwo: Long) extends StateBalance {

  def toBytes: Array[Byte] = Longs.toByteArray(balance) ++ Longs.toByteArray(balanceTwo)
  override def toString: String = s"DualBalance[${balance} | ${balanceTwo}](${Hex.toHexString(toBytes)})"
}
