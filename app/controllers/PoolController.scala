
package controllers

import org.ergoplatform.appkit.{Address, Eip4Token, ErgoId, ErgoToken, Parameters}
import play.api.{Configuration, Logger}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import java.time.LocalDateTime
import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import _root_.io.swagger.annotations._
import actors.BlockingDbWriter.{InsertNewPoolInfo, UpdatePoolInfo}
import actors.DbConnectionManager.NewConnectionRequest
import actors.QuickDbReader
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import models.ResponseModels._
import io.getblok.subpooling_core.groups.{GenesisGroup, GroupManager}
import io.getblok.subpooling_core.groups.builders.GenesisBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.EmptySelector
import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models.{DbConn, MinerSettings, PoolInformation, PoolMember, PoolPlacement, PoolState}
import play.api.libs.json.{Json, Writes}
import actors.QuickDbReader._
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.emissions.EmissionsContract
import io.getblok.subpooling_core.contracts.holding.TokenHoldingContract
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.registers.PoolFees

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.language.postfixOps
import scala.util.Try

@Api(value = "/pools", description = "Pool operations")
@Singleton
class PoolController @Inject()(@Named("quick-db-reader") quickQuery: ActorRef, @Named("blocking-db-writer") slowWrite: ActorRef,
                                val components: ControllerComponents, system: ActorSystem, config: Configuration)
extends SubpoolBaseController(components, config){
  val log: Logger = Logger("GenerationController")
  val quickQueryContext: ExecutionContext = system.dispatchers.lookup("subpool-contexts.quick-query-dispatcher")
  val slowWriteContext: ExecutionContext = system.dispatchers.lookup("subpool-contexts.blocking-io-dispatcher")
  implicit val timeOut: Timeout = Timeout(15 seconds)

  log.info("Initiating pool controller")
  @ApiOperation(
    value = "Creates a new set of pools",
    notes = "Returns PoolGenerated response",
    httpMethod = "GET"
  )
  @ApiResponses(Array(
    new ApiResponse(code = 200, response = classOf[PoolGenerated], message = "Success"),
    new ApiResponse(code = 500, message = "An error occurred while generating the pool")
  ))
  // TODO: Change to POST
  def createPool(num: Int, name:String, creator: String): Action[AnyContent] = Action {
    client.execute{
      ctx =>
        val empty   = new EmptySelector
        val builder = new GenesisBuilder(num, Parameters.MinFee)
        val pool    = new Pool(ArrayBuffer.empty[Subpool])
        val group   = new GenesisGroup(pool, ctx, wallet, Parameters.MinFee)

        val groupManager = new GroupManager(group, builder, empty)
        groupManager.initiate()

        if(groupManager.isSuccess){
          val currentTime = LocalDateTime.now()
          val poolStates = for(subPool <- group.newPools)
            yield PoolState(subPool.token.toString, subPool.id, name, subPool.box.getId.toString, group.completedGroups.values.head.getId,
              0L, 0L, subPool.box.genesis, ctx.getHeight.toLong, PoolState.CONFIRMED, 0, 0L, creator, "none", 0L,
              currentTime, currentTime)
          log.info("Creating new pool with tag " + poolStates.head.subpool)

          DataTable.createSubpoolPartitions(dbConn, poolStates.head.subpool)
          infoTable.insertNewInfo(PoolInformation(group.newPools.head.token.toString, 0L, num, 0L, 0L, 0L, 0L, PoolInformation.CURR_ERG, PoolInformation.PAY_PPLNS,
            PoolFees.POOL_FEE_CONST, official = true, 5L, 10L, name, creator, LocalDateTime.now(), LocalDateTime.now()))
          stateTable.insertStateArray(poolStates.toArray)
          Ok(Json.prettyPrint(Json.toJson(PoolGenerated(name, poolStates.head.subpool, num,
            groupManager.completedGroups.values.head.getId, creator, ctx.getHeight.toLong, currentTime.toString))))
        }else{
          InternalServerError("ERROR 500: An internal server error occurred while generating the pool")
        }
    }

  }

  def createEmissions(tag: String, currency: String, emissionReward: Double): Action[AnyContent] =  Action {
    if(currency == "test"){
      client.execute {
        ctx =>
          val poolCreator = infoTable.queryPool(tag).creator
          val metadataContract = MetadataContract.generateMetadataContract(ctx)
          val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
          val emissionsContract = EmissionsContract.generate(ctx, wallet.p2pk, Address.create(poolCreator), holdingContract)


          val distributionToken = new ErgoToken(PoolInformation.TEST_ID, 500000 * Parameters.OneErg)

          val inputBoxes     = ctx.getCoveringBoxesFor(wallet.p2pk, Parameters.MinFee * 10, Seq().asJava).getBoxes
          val emissionsToken = new Eip4Token(inputBoxes.get(0).getId.toString, 1L, "GetBlok.io Token Emissions Test", "Test token identifying an emissions box", 0)

          val outBox     = ctx.newTxBuilder().outBoxBuilder()
            .contract(wallet.pk.contract)
            .value(Parameters.MinFee * 10)
            .mintToken(emissionsToken)
            .build()
          val unsignedTokenTx = ctx.newTxBuilder().boxesToSpend(inputBoxes).outputs(outBox).fee(Parameters.MinFee).sendChangeTo(wallet.p2pk.getErgoAddress).build()
          val signedTokenTx   = wallet.prover.sign(unsignedTokenTx)
          val tokenTxId       = ctx.sendTransaction(signedTokenTx).replace("\"", "")
          val tokenInputBox   = outBox.convertToInputWith(tokenTxId, 0.toShort)


          val tokenInputs = ctx.getCoveringBoxesFor(wallet.p2pk, Parameters.MinFee, Seq(distributionToken).asJava).getBoxes
            .asScala.toSeq.filter(i => i.getTokens.size() > 0).filter(i => i.getTokens.get(0).getId == distributionToken.getId)
          val emissionsOutBox = EmissionsContract.buildGenesisBox(ctx, emissionsContract,
            Helpers.ergToNanoErg(emissionReward), Address.create(poolCreator), emissionsToken.getId, distributionToken)
          val txB = ctx.newTxBuilder()
          val unsigned = txB.boxesToSpend((Seq(tokenInputBox)++tokenInputs).asJava)
            .outputs(emissionsOutBox)
            .fee(Parameters.MinFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .build()
          val signed = wallet.prover.sign(unsigned)
          val txId = ctx.sendTransaction(signed)

          infoTable.updatePoolEmissions(tag, PoolInformation.CURR_TEST_TOKENS, emissionsToken.getId.toString, PoolInformation.TokenExchangeEmissions)
          Ok(s"EmissionBox created in transaction: ${txId}")
      }
    }else{
      InternalServerError("Something went wrong dawg")
    }
  }

  def updatePoolInfo(tag: String): Action[AnyContent] = Action {
    implicit val ec: ExecutionContext = slowWriteContext
    val fPoolStates = quickQuery ? QueryAllSubPools(tag)
    val fPoolMembers = quickQuery ? AllPoolMembers(tag)

    for{
      states <- fPoolStates.mapTo[Seq[PoolState]]
      members <- fPoolMembers.mapTo[Seq[PoolMember]]
    } yield slowWrite ! UpdatePoolInfo(tag, states.head.g_epoch, states.maxBy(s => s.block).block, members.count(m => m.g_epoch == states.head.g_epoch),
      states.map(s => s.stored_val).sum, members.map(m => m.paid).sum)

    Ok(s"Pool Information for pool ${tag} was updated")
  }

  def insertDefaultInfo(tag: String, numSubpools: Long, title: String, creator: String): Action[AnyContent] = Action.async {
    val poolInformation = PoolInformation(tag, 0L, numSubpools, 0L, 0L, 0L, 0L, PoolInformation.CURR_ERG, PoolInformation.PAY_PPLNS,
      100000L, official = true, 5L, 10L, title, creator, LocalDateTime.now(), LocalDateTime.now())

    val writePool = slowWrite ? InsertNewPoolInfo(poolInformation)
    writePool.mapTo[Long].map{
      r =>
        if(r > 0)
          okJSON(poolInformation)
        else
          InternalServerError("There was an error writing information for the pool")
    }(slowWriteContext)
  }


  // States
  def getAllPools: Action[AnyContent] = Action.async {
    val states = quickQuery ? QueryAllPoolInfo
    states.mapTo[Seq[PoolInformation]].map(s => okJSON(s))(quickQueryContext)
  }

  def getOfficialPools: Action[AnyContent] = Action.async {
    val info = quickQuery ? QueryPoolsWithOfficial(true)
    info.mapTo[Seq[PoolInformation]].map(i => okJSON(i))(quickQueryContext)
  }
  def getPoolInfo(tag: String): Action[AnyContent] = Action.async {
    val info = quickQuery ? QueryPoolInfo(tag)
    info.mapTo[PoolInformation].map(i => okJSON(i))(quickQueryContext)
  }

  def getPoolStates(tag: String): Action[AnyContent] = Action.async {
    val states = quickQuery ? QueryAllSubPools(tag)
    states.mapTo[Seq[PoolState]].map(s => okJSON(s))(quickQueryContext)
  }

  def getSubpool(tag: String, id: Long): Action[AnyContent] = Action.async {
    val states = quickQuery ? QuerySubPool(tag, id)
    states.mapTo[PoolState].map(s => okJSON(s))(quickQueryContext)
  }

  // Placements
  def getPoolPlacements(tag: String): Action[AnyContent] = Action.async{
    val states = quickQuery ? PoolPlacements(tag)
    states.mapTo[Seq[PoolPlacement]].map(s => okJSON(s))(quickQueryContext)
  }
  def getPoolPlacementsByMiner(tag: String, miner: String): Action[AnyContent] = Action.async{
    val states = quickQuery ? PlacementsByMiner(tag, miner)
    states.mapTo[Seq[PoolPlacement]].map(s => okJSON(s))(quickQueryContext)
  }
  def getPoolPlacementsByBlock(tag: String, block: Long): Action[AnyContent] = Action.async{
    val states = quickQuery ? PlacementsByBlock(tag, block)
    states.mapTo[Seq[PoolPlacement]].map(s => okJSON(s))(quickQueryContext)
  }

  def getSubPoolPlacements(tag: String, id: Long): Action[AnyContent] = Action.async{
    val states = quickQuery ? SubPoolPlacements(tag, id, None)
    states.mapTo[Seq[PoolState]].map(s => okJSON(s))(quickQueryContext)
  }

  // Members queries
  def getSubPoolMembers(tag: String, id: Long, epoch: Long): Action[AnyContent] = Action.async{
    val states = quickQuery ? SubPoolMembersByEpoch(tag, id, epoch)
    states.mapTo[Seq[PoolMember]].map(s => okJSON(s))(quickQueryContext)
  }

  def getSubPoolMembersAtGEpoch(tag: String, id: Long, gEpoch: Long): Action[AnyContent] = Action.async{
    val states = quickQuery ? SubPoolMembersByGEpoch(tag, id, gEpoch)
    states.mapTo[Seq[PoolMember]].map(s => okJSON(s))(quickQueryContext)
  }

  def getSubPoolMembersAtBlock(tag: String, id: Long, block: Long): Action[AnyContent] = Action.async{
    val states = quickQuery ? SubPoolMembersByBlock(tag, id, block)
    states.mapTo[Seq[PoolMember]].map(s => okJSON(s))(quickQueryContext)
  }

  def getPoolMembersAtGEpoch(tag: String, gEpoch: Long): Action[AnyContent] = Action.async{
    val states = quickQuery ? PoolMembersByGEpoch(tag, gEpoch)
    states.mapTo[Seq[PoolMember]].map(s => okJSON(s))(quickQueryContext)
  }

  def getPoolMembersAtBlock(tag: String, block: Long): Action[AnyContent] = Action.async{
    val states = quickQuery ? PoolMembersByBlock(tag, block)
    states.mapTo[Seq[PoolMember]].map(s => okJSON(s))(quickQueryContext)
  }
  def getAssignedMembers(tag: String): Action[AnyContent] = Action.async{
    val settings = quickQuery ? MinersByAssignedPool(tag)
    settings.mapTo[Seq[MinerSettings]].map(ms => okJSON(ms.map(m => m.address)))(quickQueryContext)
  }

  def addMiner(tag: String, miner: String): Action[AnyContent] = Action {

    val tryUpdate = Try(settingsTable.updateMinerPool(miner, tag))
    if(tryUpdate.getOrElse(0L) == 1L)
      Ok(s"Miner $miner was added to pool with tag $tag")
    else
      InternalServerError("ERROR 500: An internal server error occurred while adding the miner.")
  }


}
