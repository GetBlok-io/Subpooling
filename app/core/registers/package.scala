package io.getblok.subpooling
package core

import org.ergoplatform.appkit.{ErgoType, Parameters}
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.Coll

package object registers {

  /**
   * Returns new collection of type Coll[T] where T must have some corresponding ErgoType ErgoType[T]
   */
  def newColl[T](list: List[T], ergoType: ErgoType[T]): Coll[T] = {
    special.collection.Builder.DefaultCollBuilder.fromItems(list:_*)(ergoType.getRType)
  }
  def newColl[T](arr: Array[T], ergoType: ErgoType[T]): Coll[T] = {
    special.collection.Builder.DefaultCollBuilder.fromArray(arr)(ergoType.getRType)
  }
  def toErg(nanoErg: Long): Double = (BigDecimal(nanoErg) / Parameters.OneErg).doubleValue()
  def toNanoErg(erg: Double): Long = (BigDecimal(erg) * Parameters.OneErg).longValue()

  val serializer: ErgoTreeSerializer = new ErgoTreeSerializer

}
