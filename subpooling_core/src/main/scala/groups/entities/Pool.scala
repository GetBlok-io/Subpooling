package io.getblok.subpooling_core
package groups.entities

import io.getblok.subpooling_core.boxes.MetadataInputBox
import org.ergoplatform.appkit.{ErgoId, SignedTransaction}

import scala.collection.mutable.ArrayBuffer

class Pool(pools: ArrayBuffer[Subpool]) {
  val subPools: ArrayBuffer[Subpool] = pools.sortBy(p => p.id)
  // TODO: Possibly change due to inaccuracy with how gEpoch works

  val gEpochFromPools: Long = if (subPools.nonEmpty) subPools.head.epoch else 0
  var globalEpoch: Long = gEpochFromPools
  val token: Option[ErgoId] = if (subPools.nonEmpty) Option(subPools.head.token) else None

  var rootTx: SignedTransaction = _

  def getMetadata: Array[MetadataInputBox] = subPools.map(s => s.box).toArray

}
