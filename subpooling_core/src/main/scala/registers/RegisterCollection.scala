package io.getblok.subpooling_core
package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll


abstract class RegisterCollection[T](arr: Array[T]) {
  def ergoType: ErgoType[T]
  def coll:     Coll[T]
  def ergoVal:  ErgoValue[Coll[T]]
  def size:     Int                 = arr.length
}
