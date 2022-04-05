package io.getblok.subpooling
package controllers

import core.groups._
import core.groups.builders._
import core.groups.selectors._
import core.groups.entities._
import core.persistence.models.Models._

import org.ergoplatform.appkit.Parameters
import play.api.Configuration
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.collection.mutable.ArrayBuffer
@Singleton
class GenController @Inject()(val components: ControllerComponents, config: Configuration)
extends SubpoolBaseController(components, config){

  def createPool: Action[AnyContent] = Action {
    client.execute{
      ctx =>
        val empty   = new EmptySelector
        val builder = new GenesisBuilder(100, Parameters.MinFee)
        val pool    = new Pool(ArrayBuffer.empty[Subpool])
        val group   = new GenesisGroup(pool, ctx, wallet, Parameters.MinFee)

        val groupManager = new GroupManager(group, builder, empty)
        groupManager.initiate()

        if(groupManager.isSuccess){
          val poolStates = for(subPool <- group.newPools)
            yield PoolState(subPool.token.toString, subPool.id, "Basic Pool", subPool.box.getId.toString, group.completedGroups.values.head.getId,
              0L, 0L, subPool.box.genesis, ctx.getHeight.toLong, PoolState.CONFIRMED, 0, 0L, "none", "none", 0L,
              LocalDateTime.now(), LocalDateTime.now())

          stateTable.insertStateArray(poolStates.toArray)
          Ok("Success")
        }else{
          NotModified
        }
    }

  }
}
