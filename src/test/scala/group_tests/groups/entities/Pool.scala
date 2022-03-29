package group_tests.groups.entities

import org.ergoplatform.appkit.ErgoId

import scala.collection.mutable.ArrayBuffer

class Pool(pools: ArrayBuffer[Subpool]){
  val subPools: ArrayBuffer[Subpool] = pools.sortBy(p => p.id)
  val globalEpoch: Long = subPools.head.epoch
  val token: ErgoId = subPools.head.token

}
