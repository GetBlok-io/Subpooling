package io.getblok.subpooling_core
package registers

import org.ergoplatform.appkit.{Address, ErgoType, ErgoValue, NetworkType}
import sigmastate.Values
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * Register representing a simple long
 */
class LongReg(val value: Long) {

  def ergoType: ErgoType[Long]        = ErgoType.longType()
  def ergoVal:  ErgoValue[Long]       = ErgoValue.of(value)

  override def toString: String =
    s"LONG(${value}L)"

  override def equals(obj: Any): Boolean =
    obj match {
      case asLong if obj.isInstanceOf[Long] =>
        asLong.asInstanceOf[Long] == value
      case asErgo if obj.isInstanceOf[ErgoValue[Long]] =>
        asErgo.asInstanceOf[ErgoValue[Long]].getValue == value
      case _ =>
        false
    }
}

object LongReg {
  def ofErgo(ergoValue: ErgoValue[Long]) =
    new LongReg(ergoValue.getValue)

  def ofLong(long: Long) = new LongReg(long)

  def apply(long: Long): LongReg = ofLong(long)
}


