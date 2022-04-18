package controllers

import _root_.io.swagger.annotations._
import actors.GroupRequestHandler.{DistributionResponse, ExecuteDistribution}
import actors.QuickDbReader
import actors.QuickDbReader.{BlockByHeight, BlockById, BlocksByStatus}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import configs.Contexts
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, SimpleHoldingContract}
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager, HoldingGroup}
import io.getblok.subpooling_core.groups.builders.{DistributionBuilder, HoldingBuilder}
import io.getblok.subpooling_core.groups.entities.{Member, Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.{LoadingSelector, StandardSelector}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.payments.ShareHandler
import io.getblok.subpooling_core.persistence.{MembersTable, PlacementTable}
import io.getblok.subpooling_core.persistence.models.Models.{Block, DbConn, PoolMember, PoolState}
import models.ResponseModels._
import org.ergoplatform.appkit.{ErgoId, NetworkType, Parameters}
import QuickDbReader._
import actors.DbConnectionManager.NewConnectionRequest
import org.slf4j.LoggerFactory
import play.api.{Configuration, Logger}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.language.postfixOps
@Api(value = "/blocks")
@Singleton
class BlocksController @Inject()(@Named("group-handler") groupRequestHandler: ActorRef,
                                 @Named("quick-db-reader") quickQuery: ActorRef, system: ActorSystem,
                                 val components: ControllerComponents, config: Configuration)
extends SubpoolBaseController(components, config){
  val quickQueryContext: ExecutionContext = system.dispatchers.lookup("subpool-contexts.quick-query-dispatcher")
  val groupContext: ExecutionContext      = system.dispatchers.lookup("subpool-contexts.group-dispatcher")


  implicit val timeOut: Timeout = Timeout(5 seconds)
  private val logger: Logger    = Logger("BlocksController")



  logger.info("Initiating blocks controller")

  def getBlock(blockHeight: Long): Action[AnyContent] = Action.async {
    val block = quickQuery ? BlockByHeight(blockHeight)
    block.mapTo[Block].map(b => Ok(Json.prettyPrint(Json.toJson(b))))(quickQueryContext)
  }
  def getBlockById(id: Long): Action[AnyContent] = Action.async {
    val block = quickQuery ? BlockById(id)
    block.mapTo[Block].map(b => Ok(Json.prettyPrint(Json.toJson(b))))(quickQueryContext)
  }
  def getBlocksByStatus(status: String): Action[AnyContent] = Action.async {
    val blocks = quickQuery ? BlocksByStatus(status)
    blocks.mapTo[Seq[Block]].map(b =>
      Ok(Json.prettyPrint(Json.toJson(b))))(quickQueryContext)
  }

  def placementTable(part: String) = new PlacementTable(dbConn, part)
  def memTable(part: String) = new MembersTable(dbConn, part)

}
