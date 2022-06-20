package io.getblok.subpooling_core
package groups.selectors

import global.AppParameters
import groups.entities.{Member, Pool}
import groups.models.GroupSelector
import persistence.models.Models.PoolPlacement
import registers.{MemberInfo, PropBytes, ShareDistribution}

import org.ergoplatform.appkit.Address
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer

class LoadingSelector(placements: Array[PoolPlacement]) extends GroupSelector {

  var membersAdded: ArrayBuffer[Member] = ArrayBuffer.empty[Member]
  var membersRemoved: ArrayBuffer[Member] = ArrayBuffer.empty[Member]

  def loadPlacements: LoadingSelector = {
    for (subpool <- pool.subPools) {
      val subpoolPlacements = placements.filter(p => p.subpool_id == subpool.id)
      val logger: Logger = LoggerFactory.getLogger("LoadingSelector")
      //logger.info(s"Now loading placements for subpool ${subpool.id} in pool ${subpool.token.toString}")
      if(subpoolPlacements.nonEmpty) {
//        val filteredPlacements = subpoolPlacements.filter(sp => sp.amount > 0 || (sp.amount == 0 &&
//          (subpool.box.shareDistribution.dist
//          .exists(p => p._1.address.toString == sp.miner && p._2.getStored > 0) || sp.score > 0)))
        //logger.info(s"Placements were found for subpool ${subpool.id}! Total of ${subpoolPlacements.length} placements were found!")
        val dist = new ShareDistribution(subpoolPlacements.map(p => PropBytes.ofAddress(Address.create(p.miner))(AppParameters.networkType) ->
          new MemberInfo(Array(p.score, p.minpay, 0L, p.epochs_mined, 0L))).toMap)
        subpool.nextDist = dist
      }
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
