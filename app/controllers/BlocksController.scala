package controllers

import _root_.io.swagger.annotations._
import actors.QuickDbReader._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolBlock
import io.getblok.subpooling_core.persistence.{MembersTable, PlacementTable}
import models.ResponseModels._
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
@Api(value = "/blocks")
@Singleton
class BlocksController @Inject()(@Named("group-handler") groupRequestHandler: ActorRef,
                                 @Named("quick-db-reader") query: ActorRef, system: ActorSystem,
                                 val components: ControllerComponents, config: Configuration,
                                 override protected val dbConfigProvider: DatabaseConfigProvider
                                ) extends SubpoolBaseController(components, config)
                                with HasDatabaseConfigProvider[PostgresProfile] {
  implicit val quickQueryContext: ExecutionContext = system.dispatchers.lookup("subpool-contexts.quick-query-dispatcher")
  val groupContext: ExecutionContext      = system.dispatchers.lookup("subpool-contexts.group-dispatcher")


  implicit val timeOut: Timeout = Timeout(40 seconds)
  private val logger: Logger    = Logger("BlocksController")
  import dbConfig.profile.api._


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
    db.run(Tables.PoolBlocksTable.filter(_.status === status).drop(page.get * pageSize.get).take(pageSize.get).result).map(okJSON(_))
  }

  def allBlocks(page: Option[Int] = Some(0), pageSize: Option[Int] = Some(10), poolTag: Option[String] = None): Action[AnyContent] = Action.async {
    poolTag match {
      case Some(tag) =>
        db.run(Tables.PoolBlocksTable.filter(_.poolTag === tag).sortBy(_.created.desc)
          .drop(page.get * pageSize.get)
          .take(pageSize.get)
          .result).map(okJSON(_))
      case None =>
        db.run(Tables.PoolBlocksTable.sortBy(_.created.desc).drop(page.get * pageSize.get).take(pageSize.get)
          .result).map(okJSON(_))
    }

  }
  def blocksPage(page: Int, pageSize: Int, poolTag: Option[String] = None): Action[AnyContent] = Action.async {
    poolTag match {
      case Some(tag) =>
        db.run(Tables.PoolBlocksTable.filter(_.poolTag === tag).sortBy(_.created.desc)
          .drop(page * pageSize)
          .take(pageSize)
          .result).map(okJSON(_))
      case None =>
        db.run(Tables.PoolBlocksTable.sortBy(_.created.desc).drop(page * pageSize).take(pageSize)
          .result).map(okJSON(_))
    }

  }

  def poolBlocksPage(page: Int, pageSize: Int, poolTag: String): Action[AnyContent] = Action.async {
    db.run(Tables.PoolBlocksTable.filter(_.poolTag === poolTag).sortBy(_.created.desc)
      .drop(page * pageSize)
      .take(pageSize)
      .result).map(okJSON(_))
  }


}
