package io.getblok.subpooling_core
package registers

import io.getblok.subpooling_core.global.AppParameters
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{ErgoType, ErgoValue, Iso}
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * RegisterCollection representing collection of prop bytes holding operator info
 */
class PoolOperators(val arr: Array[PropBytes]){

  def apply(idx: Int):  PropBytes                  = arr(idx)

  override def toString: String = arr.mkString("OPS(", ", ", ")")

  override def equals(obj: Any): Boolean =
    obj match {
      case asArray if obj.isInstanceOf[Array[Long]] =>
        asArray.asInstanceOf[Array[Long]] sameElements arr
      case asColl if obj.isInstanceOf[Coll[Long]] =>
        asColl.asInstanceOf[Coll[Long]] == coll
      case asErgo if obj.isInstanceOf[ErgoValue[Coll[Long]]] =>
        asErgo.asInstanceOf[ErgoValue[Coll[Long]]].getValue == coll
      case asPoolInfo if obj.isInstanceOf[PoolInfo] =>
        asPoolInfo.asInstanceOf[PoolInfo].coll == coll
      case _ =>
        false
    }

  def ergoType: ErgoType[Coll[java.lang.Byte]] = ErgoType.collType(ErgoType.byteType())

  def coll: Coll[Coll[java.lang.Byte]] = Colls.fromArray(arr.map(a => a.coll))

  def ergoVal: ErgoValue[Coll[Coll[java.lang.Byte]]] = ErgoValue.of(coll, ergoType)
}

object PoolOperators {
  def ofColl(coll: Coll[Coll[java.lang.Byte]]) = {
    new PoolOperators(coll.map(c => c.asInstanceOf[Coll[Byte]].toArray).toArray.map(a => new PropBytes(a)(AppParameters.networkType)))
  }

  def ofErgo(ergoValue: ErgoValue[Coll[Coll[java.lang.Byte]]]) =
    new PoolOperators(ergoValue.getValue.toArray.map(o => new PropBytes(o.toArray.map(Iso.jbyteToByte.to))(AppParameters.networkType)))
}


