package group_tests.groups.selectors

import app.AppParameters
import group_tests.groups.entities.{Member, Pool}
import group_tests.groups.models.GroupSelector
import org.ergoplatform.appkit.Address
import persistence.models.Models.PoolPlacement
import registers.{MemberInfo, PropBytes, ShareDistribution}

import scala.collection.mutable.ArrayBuffer

class LoadingSelector(placements: Array[PoolPlacement]) extends GroupSelector{

  var membersAdded: ArrayBuffer[Member] = ArrayBuffer.empty[Member]
  var membersRemoved: ArrayBuffer[Member] = ArrayBuffer.empty[Member]

  def loadPlacements: LoadingSelector = {
    for(subpool <- pool.subPools){
      val subpoolPlacements = placements.filter(p => p.subpool_id == subpool.id)
      val dist = new ShareDistribution(subpoolPlacements.map(p => PropBytes.ofAddress(Address.create(p.miner))(AppParameters.networkType) ->
        new MemberInfo(Array(p.score, p.minpay, 0L, p.epochs_mined, 0L))).toMap)
      subpool.nextDist = dist
    }
    this
  }

  override def getSelection: Pool = {
    loadPlacements
    pool.subPools --= pool.subPools.filter(p => p.nextDist == null)
    pool.subPools --= pool.subPools.filter(p => p.nextDist.size == 0)
    pool
  }
}
