package io.getblok.subpooling_core
package plasma

import plasma.StateConversions.{balanceConversion, minerConversion, scoreConversion}

import io.getblok.getblok_plasma.ByteConversion
import io.getblok.getblok_plasma.collections.{LocalPlasmaMap, PlasmaMap, ProvenResult, ProxyPlasmaMap}
import io.getblok.subpooling_core.global.AppParameters
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore

import java.io.File

class BalanceState[T](poolTag: String)(implicit convT: ByteConversion[T]){
  val ldbStore = new LDBVersionedStore(new File(s"${AppParameters.plasmaStoragePath}/${poolTag}"), StateParams.maxVersions)

  val avlStorage = new VersionedLDBAVLStorage[Digest32](ldbStore, StateParams.treeParams.toNodeParams)(Blake2b256)
  val map = new ProxyPlasmaMap[PartialStateMiner, T](avlStorage,StateParams.treeFlags, StateParams.treeParams)


  def loadState(states: Seq[(PartialStateMiner, T)]): ProvenResult[T] = {
    map.insert(states:_*)
  }


}
