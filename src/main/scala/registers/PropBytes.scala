package registers

import app.AppParameters.networkType
import org.ergoplatform.appkit.{Address, ErgoType, ErgoValue, NetworkType}
import sigmastate.Values
import sigmastate.Values.ErgoTree
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * RegisterCollection representing PropositionalBytes of a contract
 */
class PropBytes(val arr: Array[Byte])(implicit networkType: NetworkType) extends RegisterCollection[Byte](arr){

  def ergoType: ErgoType[Byte]        = ErgoType.byteType()
  def coll:     Coll[Byte]            = Colls.fromArray(arr)
  def ergoVal:  ErgoValue[Coll[Byte]] = ErgoValue.of(coll, ergoType)
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
      case asErgo if obj.isInstanceOf[ErgoValue[Coll[Byte]]] =>
        asErgo.asInstanceOf[ErgoValue[Coll[Byte]]].getValue == coll
      case asPropBytes if obj.isInstanceOf[PropBytes] =>
        asPropBytes.asInstanceOf[PropBytes].coll == coll
      case _ =>
        false
    }
}

object PropBytes {

  def ofColl(coll: Coll[Byte])(implicit networkType: NetworkType) =
    new PropBytes(coll.toArray)

  def ofAddress(addr: Address)(implicit networkType: NetworkType) =
    new PropBytes(addr.getErgoAddress.script.bytes)

  def ofErgoTree(ergoTree: ErgoTree)(implicit networkType: NetworkType) =
    new PropBytes(ergoTree.bytes)

  def ofErgo(ergoValue: ErgoValue[Coll[Byte]])(implicit networkType: NetworkType) =
    new PropBytes(ergoValue.getValue.toArray)
}
