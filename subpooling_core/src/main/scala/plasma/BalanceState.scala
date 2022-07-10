package io.getblok.subpooling_core
package plasma

import plasma.StateConversions.{balanceConversion, minerConversion, scoreConversion}

import io.getblok.getblok_plasma.collections.{PlasmaMap, ProvenResult}
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore

import java.io.File

class BalanceState(poolTag: String, epoch: Long){
  val ldbStore = new LDBVersionedStore(new File(s"./${poolTag}/epoch_${epoch}"), StateParams.maxVersions)

  val avlStorage = new VersionedLDBAVLStorage[Digest32](ldbStore, StateParams.treeParams.toNodeParams)(Blake2b256)
  val map = new PlasmaMap[PartialStateMiner, StateBalance](avlStorage,StateParams.treeFlags, StateParams.treeParams)


  def loadState(states: Seq[(PartialStateMiner, StateBalance)]): ProvenResult[StateBalance] = {
    map.insert(states:_*)
  }
}
