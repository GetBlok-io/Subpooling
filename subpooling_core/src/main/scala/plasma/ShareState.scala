package io.getblok.subpooling_core
package plasma

import io.getblok.getblok_plasma.collections.{LocalPlasmaMap, PlasmaMap, ProvenResult}
import io.getblok.subpooling_core.plasma.StateConversions.{minerConversion, scoreConversion}
import scorex.crypto.authds.avltree.batch.{VersionedAVLStorage, VersionedLDBAVLStorage}
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore

import java.io.File

class ShareState(poolTag: String, epoch: Long){
  val ldbStore = new LDBVersionedStore(new File(s"./${poolTag}/epoch_${epoch}"), StateParams.maxVersions)

  val avlStorage = new VersionedLDBAVLStorage[Digest32](ldbStore, StateParams.treeParams.toNodeParams)(Blake2b256)
  val map = new LocalPlasmaMap[PartialStateMiner, StateScore](avlStorage,StateParams.treeFlags, StateParams.treeParams)


  def loadState(states: Seq[(PartialStateMiner, StateScore)]): ProvenResult[StateScore] = {
    map.insert(states:_*)
  }
}
