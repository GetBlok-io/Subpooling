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
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolMember, PoolState}
import models.ResponseModels._
import org.ergoplatform.appkit.{ErgoId, NetworkType, Parameters}
import QuickDbReader._
import org.slf4j.LoggerFactory
import play.api.{Configuration, Logger}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
@Api(value = "/blocks")
@Singleton
class BlocksController @Inject()(@Named("group-handler") groupRequestHandler: ActorRef, system: ActorSystem,
                                 val components: ControllerComponents, config: Configuration)
extends SubpoolBaseController(components, config){
  val quickQueryContext: ExecutionContext = system.dispatchers.lookup("quick-query-dispatcher")
  val startupContext: ExecutionContext = system.dispatchers.lookup("startup-pinned-dispatcher")

  val quickQuery: ActorRef      = system.actorOf(QuickDbReader.props)
  implicit val timeOut: Timeout = Timeout(3 seconds)
  private val logger: Logger    = Logger("BlocksController")

  def initiateBlock(blockHeight: Long): Action[AnyContent] = Action {

    val block = blocksTable.queryByHeight(blockHeight)
    require(block.status == Block.CONFIRMED, "Block must have confirmed status!")


    val poolTag = settingsTable.queryByMiner(block.miner).subpool
    val poolStates = stateTable.queryAllSubPoolStates(poolTag)

    // TODO: Maybe allow initiated as well?
    require(poolStates.forall(s => s.status == PoolState.CONFIRMED), "All subPools must have confirmed status!")


    val poolSettings = settingsTable.queryBySubpool(poolTag)
    val poolAddresses = poolSettings.map(m => m.address)



    logger.info(s"Total number of addresses assigned to this pool: ${poolAddresses.size}")

    val collector = new ShareHandler(sharesTable, PaymentType.PPLNS_WINDOW).queryToWindow(blockHeight)

    logger.info(s"Collector shareMap length before address check: ${collector.shareMap.size}")

    collector.shareMap.retain((m, s) => poolAddresses.contains(m))

    logger.info(s"Collector shareMap length: ${collector.shareMap.size}")
    logger.info(s"shareMap: ${collector.shareMap.toString()}")
    // TODO: Make pool flags (1) equal to share operator payment type

    // Collect new min payments for this placement
    var members = collector.toMembers.map{
      m => m.copy( memberInfo =
        m.memberInfo.withMinPay(
          (poolSettings.find(s => s.address == m.address.toString).get.paymentthreshold * BigDecimal(Parameters.OneErg)).longValue()
        )
      )
    }

    logger.info("Num members: " + members.length)
    val placeTable = placementTable(poolTag)
    val lastPlacement = placeTable.queryLastPlacement

    client.execute{
      ctx =>
        implicit val networkType: NetworkType = ctx.getNetworkType

        // Create pool
        val metadataBoxes = poolStates.map(s => new MetadataInputBox(ctx.getBoxesById(s.box).head, ErgoId.create(poolTag)))
        val subPools = ArrayBuffer() ++= metadataBoxes.map(m => new Subpool(m))
        val pool = new Pool(subPools)


        // Updated members with epochsMined added
        val updatedMembers = ArrayBuffer.empty[Member]

        // Use last placement to get most recent epochs mined
        if(lastPlacement.isDefined){
          val allLastPlacements = placeTable.queryPlacementsForBlock(lastPlacement.get.block)
          val lastEpochsMined = for(place <- allLastPlacements) yield place.miner -> place.epochs_mined
          // Place members based on placements
          for(member <- members){
            val lastMined = lastEpochsMined.find(em => em._1 == member.address.toString)
            if(lastMined.isDefined)
              updatedMembers += member.copy(memberInfo = member.memberInfo.withEpochs(lastMined.get._2))
            else
              updatedMembers += member
          }
          // Load members and epoch based on last placements
          pool.subPools.foreach{
            s =>
              val lastPoolPlacements = allLastPlacements.filter(p => p.subpool_id == s.id)
              if(lastPoolPlacements.nonEmpty) {
                s.members = lastPoolPlacements.map(m => m.toPartialMember).toArray
                s.epoch = lastPoolPlacements.head.epoch
              }
          }
          // Set new globalEpoch based on last placement
          pool.globalEpoch = lastPlacement.get.g_epoch
        }else{
          // If last placements not defined, take from metadata
          for(member <- members){
            val lastMined = metadataBoxes.find(m => m.shareDistribution.dist.exists(d => d._1.address == member.address))
            if(lastMined.isDefined){
              val lastEpochsMined = lastMined.get.shareDistribution.dist.find(d => d._1.address == member.address).get._2.getEpochsMined
              updatedMembers += member.copy(memberInfo = member.memberInfo.withEpochs(lastEpochsMined))
            }else{
              updatedMembers += member
            }
          }
        }
        // TODO: Make pool flag (0) next Fee change epoch, it must hit up to 5 epochs before being lowered
        members = updatedMembers.toArray

        val metadataContract = MetadataContract.generateMetadataContract(ctx)
        val holdingContract: HoldingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataContract.getAddress, ErgoId.create(poolTag))

        val standard  = new StandardSelector(members)
        val builder   = new HoldingBuilder(block.getErgReward, holdingContract, AppParameters.getBaseFees(block.getErgReward))
        val group     = new HoldingGroup(pool, ctx, wallet, blockHeight, block.getErgReward)

        val groupManager = new GroupManager(group, builder, standard)
        groupManager.initiate()

        if(groupManager.isSuccess){

          placeTable.insertPlacementArray(group.poolPlacements.toArray)
          blocksTable.updateBlockStatus(Block.INITIATED, blockHeight)
          okJSON(group.poolPlacements)
        }else{
          InternalServerError("ERROR 500: An internal server error occurred while generating placements")
        }
    }

  }

  def distributeBlock(blockHeight: Long): Action[AnyContent] = Action {
    val block = blocksTable.queryByHeight(blockHeight)
    require(block.status == Block.INITIATED, "Block must have initiated status!")


    val poolTag = settingsTable.queryByMiner(block.miner).subpool
    val poolStates = stateTable.queryAllSubPoolStates(poolTag)

    // TODO: Maybe allow initiated as well?
    require(poolStates.forall(s => s.status == PoolState.CONFIRMED), "All subPools must have confirmed status!")
    val placeTable = placementTable(poolTag)
    val lastgEpoch = poolStates.head.g_epoch
    val placements = placeTable.queryPlacementsForBlock(blockHeight)
    require(placements.nonEmpty, s"No placements found for block $blockHeight")
    require(placements.head.g_epoch == lastgEpoch + 1)
    implicit val timeOut: Timeout = Timeout(60 seconds)
    val futureMembers = groupRequestHandler ? ExecuteDistribution(poolTag, poolStates, placements, block)
    val response = Await.result(futureMembers, 60 seconds).asInstanceOf[DistributionResponse]

    if(response.nextMembers.nonEmpty && response.nextStates.nonEmpty) {
      val membersTable = memTable(poolTag)
      membersTable.insertMemberArray(response.nextMembers)
      stateTable.updateGEpoch(poolTag, lastgEpoch + 1)
      stateTable.updateManyStates(response.nextStates)

      okJSON(response)
    }else{
      InternalServerError("ERROR 500: Internal server error occurred during group execution")
    }
  }



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
