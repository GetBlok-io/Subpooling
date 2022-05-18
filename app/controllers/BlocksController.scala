package controllers

import _root_.io.swagger.annotations._
import actors.QuickDbReader._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import io.getblok.subpooling_core.persistence.models.Models.PoolBlock
import io.getblok.subpooling_core.persistence.{MembersTable, PlacementTable}
import models.ResponseModels._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
@Api(value = "/blocks")
@Singleton
class BlocksController @Inject()(@Named("group-handler") groupRequestHandler: ActorRef,
                                 @Named("quick-db-reader") query: ActorRef, system: ActorSystem,
                                 val components: ControllerComponents, config: Configuration)
extends SubpoolBaseController(components, config){
  implicit val quickQueryContext: ExecutionContext = system.dispatchers.lookup("subpool-contexts.quick-query-dispatcher")
  val groupContext: ExecutionContext      = system.dispatchers.lookup("subpool-contexts.group-dispatcher")


  implicit val timeOut: Timeout = Timeout(20 seconds)
  private val logger: Logger    = Logger("BlocksController")



  logger.info("Initiating blocks controller")

  def getBlock(blockHeight: Long): Action[AnyContent] = Action.async {
    val block = query ? BlockByHeight(blockHeight)
    block.mapTo[PoolBlock].map(b => okJSON(b))(quickQueryContext)
  }
  def getBlockById(id: Long): Action[AnyContent] = Action.async {
    val block = query ? BlockById(id)
    block.mapTo[PoolBlock].map(b => okJSON(b))(quickQueryContext)
  }
  def getBlocksByStatus(status: String, page: Option[Int] = Some(0), pageSize: Option[Int] = Some(10)): Action[AnyContent] = Action.async {
    val blocks = query ? PoolBlocksByStatus(status)
    blocks.mapTo[Seq[PoolBlock]].map(b =>
      okJSON(Paginate(b, pageSize))

    )(quickQueryContext)
  }

  def allBlocks(poolTag: Option[String], pageSize: Option[Int] = Some(10)): Action[AnyContent] = Action.async {
    val blocks = (query ? QueryBlocks(poolTag)).mapTo[Seq[PoolBlock]]
    blocks.map(b => okJSON(Paginate(b, pageSize)))
  }

  def placementTable(part: String) = new PlacementTable(dbConn, part)
  def memTable(part: String) = new MembersTable(dbConn, part)

}
