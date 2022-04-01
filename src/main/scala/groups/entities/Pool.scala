package groups.entities

import org.ergoplatform.appkit.{ErgoId, SignedTransaction}

import scala.collection.mutable.ArrayBuffer

class Pool(pools: ArrayBuffer[Subpool]){
  val subPools: ArrayBuffer[Subpool] = pools.sortBy(p => p.id)
  // TODO: Possibly change due to inaccuracy with how gEpoch works
  val globalEpoch: Long = subPools.head.epoch
  val token: ErgoId = subPools.head.token

  var rootTx: SignedTransaction = _

}
