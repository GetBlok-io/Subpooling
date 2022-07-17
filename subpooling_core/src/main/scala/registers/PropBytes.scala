package io.getblok.subpooling_core
package registers

import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{Address, ErgoType, ErgoValue, Iso, NetworkType}
import sigmastate.Values
import sigmastate.Values.ErgoTree
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * RegisterCollection representing PropositionalBytes of a contract
 */
class PropBytes(val arr: Array[Byte])(implicit networkType: NetworkType) extends RegisterCollection[java.lang.Byte](arr.map(Iso.jbyteToByte.from)){

  def ergoType: ErgoType[java.lang.Byte]        = ErgoType.byteType()
  def coll:     Coll[java.lang.Byte]            = Colls.fromArray(arr.map(Iso.jbyteToByte.from))
  def ergoVal:  ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(Colls.fromArray(arr.map(Iso.jbyteToByte.from)), ErgoType.byteType())
  def address:  Address               = Address.fromErgoTree(serializer.deserializeErgoTree(arr), networkType)

  def ergoTree: Values.ErgoTree       = serializer.deserializeErgoTree(arr)

  override def toString: String =
    s"PB(${address.toString.take(6)}...${address.toString.drop(address.toString.length - 6)})[$size bytes]"

  override def equals(obj: Any): Boolean =
    obj match {
      case asArray if obj.isInstanceOf[Array[Byte]] =>
        asArray.asInstanceOf[Array[Byte]] sameElements arr
      case asColl if obj.isInstanceOf[Coll[Byte]] =>
        asColl.asInstanceOf[Coll[Byte]] == coll
      case asErgo if obj.isInstanceOf[ErgoValue[Coll[java.lang.Byte]]] =>
        asErgo.asInstanceOf[ErgoValue[Coll[java.lang.Byte]]].getValue == coll
      case asPropBytes if obj.isInstanceOf[PropBytes] =>
        asPropBytes.asInstanceOf[PropBytes].coll == coll
      case _ =>
        false
    }
}

object PropBytes {

  def ofColl(coll: Coll[java.lang.Byte])(implicit networkType: NetworkType) =
    new PropBytes(coll.map(Iso.jbyteToByte.to).toArray)

  def ofAddress(addr: Address)(implicit networkType: NetworkType) =
    new PropBytes(addr.getErgoAddress.script.bytes)

  def ofErgoTree(ergoTree: ErgoTree)(implicit networkType: NetworkType) =
    new PropBytes(ergoTree.bytes)

  def ofErgo(ergoValue: ErgoValue[Coll[java.lang.Byte]])(implicit networkType: NetworkType) =
    new PropBytes(ergoValue.getValue.toArray.map(Iso.jbyteToByte.to))
}
