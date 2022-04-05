package io.getblok.subpooling
package core.payments

import core.persistence.SharesTable
import global.AppParameters

class ShareHandler(shareTable: SharesTable){
  val collector: ShareCollector = new ShareCollector

  def queryToWindow(blockHeight: Long): ShareCollector = {
    var offset = 0
    while(collector.totalScore < AppParameters.pplnsWindow){
      val shares = shareTable.queryNext50k(blockHeight)
      shares.foreach{
        s =>
          if(collector.totalScore < AppParameters.pplnsWindow){
            collector.addToMap(s)
          }
      }
      offset = offset + 50000
    }
    collector
  }


}
