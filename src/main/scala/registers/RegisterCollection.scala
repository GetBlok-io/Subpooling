package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import sigmastate.eval.Colls
import special.collection.Coll


abstract class RegisterCollection[T](arr: Array[T]) {
  def ergoType: ErgoType[T]
  def coll:     Coll[T]
  def ergoVal:  ErgoValue[Coll[T]]
  def size:     Int                 = arr.length
}
