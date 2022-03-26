package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue, NetworkType}
import sigmastate.eval.Colls
import special.collection.Coll

/**
 * Represents R5 of the Metadata Box within each subpool, mapping propositional bytes to fee values, where the fee byte
 * represents the percentage taken from the holding contract
 * @param fees - Map that links PropositionalBytes to Fee Byte
 */
class PoolFees(val fees: Map[PropBytes, Int]) {

  def ergoType: ErgoType[(Coll[Byte], Int)]        = ErgoType.pairType(
    ErgoType.collType(ErgoType.byteType()), ErgoType.integerType()
  )

  def coll:     Coll[(Coll[Byte], Int)]            = Colls.fromArray(
    fees.map(m => (m._1.coll, m._2)).toArray
  )

  def ergoVal: ErgoValue[Coll[(Coll[Byte], Int)]]       = ErgoValue.of(coll, ergoType)
  def size:    Int                                      = fees.size

  def apply(propBytes: PropBytes): Int                  = fees(propBytes)

  def __--(props: Iterable[PropBytes])                = new PoolFees(fees -- props)
  def __++(sd: PoolFees)                              = new PoolFees(fees ++ sd.fees)
  def filter(p: ((PropBytes, Int)) => Boolean)        = new PoolFees(fees.filter(p))

  def keys:   Iterable[PropBytes]         = fees.keys
  def values: Iterable[Int]               = fees.values
  def head:   (PropBytes, Int)            = fees.head

  override def toString: String           = fees.mkString("FEES(", ", ", ")")
  override def equals(obj: Any): Boolean  =
    obj match {
      case asMap if obj.isInstanceOf[Map[PropBytes, Int]] =>
        asMap.asInstanceOf[Map[PropBytes, Int]] == fees
      case asColl if obj.isInstanceOf[Coll[(Coll[Byte], Int)]] =>
        asColl.asInstanceOf[Coll[(Coll[Byte], Int)]] == coll
      case asErgo if obj.isInstanceOf[ErgoValue[Coll[(Coll[Byte], Int)]]] =>
        asErgo.asInstanceOf[ErgoValue[Coll[(Coll[Byte], Int)]]].getValue == coll
      case asFeeMap if obj.isInstanceOf[PoolFees] =>
        asFeeMap.asInstanceOf[PoolFees].coll == coll
      case _ =>
        false
    }
}

object PoolFees {
  def ofColl(coll: Coll[(Coll[Byte], Int)])(implicit networkType: NetworkType) =
    new PoolFees(coll.toArray.map(fm => (PropBytes.ofColl(fm._1), fm._2)).toMap)

  def ofErgo(ergoValue: ErgoValue[Coll[(Coll[Byte], Int)]])(implicit networkType: NetworkType) =
    new PoolFees(ergoValue.getValue.toArray.map(fm => (PropBytes.ofColl(fm._1), fm._2)).toMap)

  final val POOL_FEE_CONST = 100000
}




