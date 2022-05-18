//package tasks
//
//import actors.BlockingDbWriter._
//import actors.ExplorerRequestBus.ExplorerRequests.{BoxesById, FatalExplorerError, TimeoutError, TxById}
//import actors.QuickDbReader._
//import akka.actor.{ActorRef, ActorSystem}
//import akka.pattern.ask
//import akka.util.Timeout
//import configs.TasksConfig.TaskConfiguration
//import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
//import io.getblok.subpooling_core.contracts.MetadataContract
//import io.getblok.subpooling_core.contracts.emissions.EmissionsContract
//import io.getblok.subpooling_core.contracts.holding.TokenHoldingContract
//import io.getblok.subpooling_core.explorer.Models.{Output, TransactionData}
//import io.getblok.subpooling_core.global.AppParameters.NodeWallet
//import io.getblok.subpooling_core.global.{AppParameters, Helpers}
//import io.getblok.subpooling_core.groups.{GenesisGroup, GroupManager}
//import io.getblok.subpooling_core.groups.builders.GenesisBuilder
//import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
//import io.getblok.subpooling_core.groups.selectors.EmptySelector
//import io.getblok.subpooling_core.persistence.models.DataTable
//import io.getblok.subpooling_core.persistence.models.Models._
//import io.getblok.subpooling_core.registers.PoolFees
//import models.DatabaseModels.{Balance, BalanceChange, Payment}
//import models.ResponseModels.PoolGenerated
//import org.ergoplatform.appkit.{Address, Eip4Token, ErgoClient, ErgoId, ErgoToken}
//import persistence.Tables
//import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
//import play.api.libs.json.Json
//import play.api.mvc.{Action, AnyContent}
//import play.api.{Configuration, Logger}
//import slick.jdbc.PostgresProfile
//import utils.PoolTemplates.PoolTemplate
//
//import java.time.{Instant, LocalDateTime, ZoneOffset}
//import javax.inject.{Inject, Named, Singleton}
//import scala.collection.mutable.ArrayBuffer
//import scala.concurrent.duration.DurationInt
//import scala.concurrent.{ExecutionContext, Future}
//import scala.language.{higherKinds, postfixOps}
//import scala.util.{Failure, Success, Try}
//
//@Singleton
//class InitializePoolTask @Inject()(system: ActorSystem, config: Configuration,
//                                   @Named("quick-db-reader") query: ActorRef,
//                                   @Named("blocking-db-writer") write: ActorRef,
//                                   @Named("explorer-req-bus") expReq: ActorRef,
//                                   protected val dbConfigProvider: DatabaseConfigProvider)
//                                    extends HasDatabaseConfigProvider[PostgresProfile]{
//
//  import dbConfig.profile.api._
//
//  val logger: Logger = Logger("InitializePoolTask")
//  val taskConfig = new TasksConfig(config)
//  val nodeConfig: NodeConfig        = new NodeConfig(config)
//  val client: ErgoClient = nodeConfig.getClient
//  val wallet:     NodeWallet = nodeConfig.getNodeWallet
//
//  val contexts: Contexts = new Contexts(system)
//  val params: ParamsConfig = new ParamsConfig(config)
//  var blockQueue: ArrayBuffer[Block] = ArrayBuffer.empty[Block]
//
//  implicit val ec: ExecutionContext = contexts.taskContext
//
//  case class UninitializedPool(poolMade: Boolean, emissionsMade: Option[Boolean], template: PoolTemplate)
//
//  if(taskConfig.enabled) {
//    logger.info(db.source.toString)
//    logger.info(dbConfig.profileName)
//    logger.info(s"DatabaseCrossCheck will initiate in ${taskConfig.startup.toString()} with an interval of " +
//      s" ${taskConfig.interval}")
//    system.scheduler.scheduleAtFixedRate(initialDelay = taskConfig.startup, interval = taskConfig.interval)({
//      () =>
//
//        Try {
//          checkLastPool
//        }.recoverWith{
//          case ex =>
//            logger.error("There was a critical error while checking processing blocks!", ex)
//            Failure(ex)
//        }
//        Try(createNextPool).recoverWith{
//          case ex =>
//            logger.error("There was a critical error while checking distributions!", ex)
//            Failure(ex)
//        }
//    })(contexts.taskContext)
//  }
//
//  def checkLastPool = {
//    implicit val timeout: Timeout = Timeout(15 seconds)
//    val queryBlocks = (query ? PoolBlocksByStatus(PoolBlock.PROCESSING)).mapTo[Seq[PoolBlock]]
//  }
//  def createNextPool = {
//
//  }
//
//  // TODO: Change to POST
//  def createPool(num: Int, name:String, creator: String): Action[AnyContent] = {
//    client.execute{
//      ctx =>
//        val empty   = new EmptySelector
//        val builder = new GenesisBuilder(num, Helpers.MinFee)
//        val pool    = new Pool(ArrayBuffer.empty[Subpool])
//        val group   = new GenesisGroup(pool, ctx, wallet, Helpers.MinFee)
//
//        val groupManager = new GroupManager(group, builder, empty)
//        groupManager.initiate()
//
//        if(groupManager.isSuccess){
//          val currentTime = LocalDateTime.now()
//          val poolStates = for(subPool <- group.newPools)
//            yield PoolState(subPool.token.toString, subPool.id, name, subPool.box.getId.toString, group.completedGroups.values.head.getId,
//              0L, 0L, subPool.box.genesis, ctx.getHeight.toLong, PoolState.CONFIRMED, 0, 0L, creator, "none", 0L,
//              currentTime, currentTime)
//          log.info("Creating new pool with tag " + poolStates.head.subpool)
//
//          DataTable.createSubpoolPartitions(dbConn, poolStates.head.subpool)
//          infoTable.insertNewInfo(PoolInformation(group.newPools.head.token.toString, 0L, num, 0L, 0L, 0L, 0L, PoolInformation.CURR_ERG, PoolInformation.PAY_PPLNS,
//            PoolFees.POOL_FEE_CONST, official = true, 5L, 10L, name, creator, LocalDateTime.now(), LocalDateTime.now()))
//          stateTable.insertStateArray(poolStates.toArray)
//          Ok(Json.prettyPrint(Json.toJson(PoolGenerated(name, poolStates.head.subpool, num,
//            groupManager.completedGroups.values.head.getId, creator, ctx.getHeight.toLong, currentTime.toString))))
//        }else{
//          InternalServerError("ERROR 500: An internal server error occurred while generating the pool")
//        }
//    }
//
//  }
//
//  def createEmissions(tag: String, currency: String, emissionReward: Double): Action[AnyContent] = {
//    currency match {
//      case PoolInformation.CURR_TEST_TOKENS =>
//      client.execute {
//        ctx =>
//          val poolCreator = infoTable.queryPool(tag).creator
//          val metadataContract = MetadataContract.generateMetadataContract(ctx)
//          val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
//          val emissionsContract = EmissionsContract.generate(ctx, wallet.p2pk, Address.create(poolCreator), holdingContract)
//
//
//          val distributionToken = new ErgoToken(PoolInformation.TEST_ID, 500000 * Helpers.OneErg)
//
//          val inputBoxes     = ctx.getCoveringBoxesFor(wallet.p2pk, Helpers.MinFee * 10, Seq().asJava).getBoxes
//          val emissionsToken = new Eip4Token(inputBoxes.get(0).getId.toString, 1L, "GetBlok.io Token Emissions Test", "Test token identifying an emissions box", 0)
//
//          val outBox     = ctx.newTxBuilder().outBoxBuilder()
//            .contract(wallet.pk.contract)
//            .value(Helpers.MinFee * 10)
//            .mintToken(emissionsToken)
//            .build()
//          val unsignedTokenTx = ctx.newTxBuilder().boxesToSpend(inputBoxes).outputs(outBox).fee(Helpers.MinFee).sendChangeTo(wallet.p2pk.getErgoAddress).build()
//          val signedTokenTx   = wallet.prover.sign(unsignedTokenTx)
//          val tokenTxId       = ctx.sendTransaction(signedTokenTx).replace("\"", "")
//          val tokenInputBox   = outBox.convertToInputWith(tokenTxId, 0.toShort)
//
//
//          val tokenInputs = ctx.getCoveringBoxesFor(wallet.p2pk, Helpers.MinFee, Seq(distributionToken).asJava).getBoxes
//            .asScala.toSeq.filter(i => i.getTokens.size() > 0).filter(i => i.getTokens.get(0).getId == distributionToken.getId)
//          val emissionsOutBox = EmissionsContract.buildGenesisBox(ctx, emissionsContract,
//            Helpers.ergToNanoErg(emissionReward), Address.create(poolCreator), emissionsToken.getId, distributionToken)
//          val txB = ctx.newTxBuilder()
//          val unsigned = txB.boxesToSpend((Seq(tokenInputBox)++tokenInputs).asJava)
//            .outputs(emissionsOutBox)
//            .fee(Helpers.MinFee)
//            .sendChangeTo(wallet.p2pk.getErgoAddress)
//            .build()
//          val signed = wallet.prover.sign(unsigned)
//          val txId = ctx.sendTransaction(signed)
//
//          infoTable.updatePoolEmissions(tag, PoolInformation.CURR_TEST_TOKENS, emissionsToken.getId.toString, PoolInformation.TokenExchangeEmissions)
//
//      }
//    }
//  }
//}
