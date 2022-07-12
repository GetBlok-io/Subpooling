package tasks

import actors.BlockingDbWriter._
import actors.ExplorerRequestBus.ExplorerRequests.{BoxesById, FatalExplorerError, TimeoutError, TxById}
import actors.QuickDbReader._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.emissions.{EmissionsContract, ExchangeContract, ProportionalEmissionsContract}
import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.explorer.Models.{Output, TransactionData}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups.{GenesisGroup, GroupManager}
import io.getblok.subpooling_core.groups.builders.GenesisBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.EmptySelector
import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models._
import io.getblok.subpooling_core.registers.PoolFees
import models.DatabaseModels.{Balance, BalanceChange, Payment}
import models.ResponseModels.PoolGenerated
import org.ergoplatform.appkit.{Address, ConstantsBuilder, Eip4Token, ErgoClient, ErgoId, ErgoToken, ErgoValue, InputBox, NetworkType}
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile
import utils.{EmissionTemplates, PoolTemplates}
import utils.PoolTemplates.{PoolTemplate, UninitializedPool}

import java.time.{Instant, LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Named, Singleton}
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{higherKinds, postfixOps}
import scala.util.{Failure, Success, Try}

@Singleton
class InitializePoolTask @Inject()(system: ActorSystem, config: Configuration,
                                   @Named("quick-db-reader") query: ActorRef,
                                   @Named("blocking-db-writer") write: ActorRef,
                                   @Named("explorer-req-bus") expReq: ActorRef,
                                   protected val dbConfigProvider: DatabaseConfigProvider)
                                    extends HasDatabaseConfigProvider[PostgresProfile]{

  import dbConfig.profile.api._

  val logger: Logger = Logger("InitializePoolTask")
  val tasks = new TasksConfig(config)
  val taskConfig: TaskConfiguration = tasks.initPoolConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val client: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)
  var blockQueue: ArrayBuffer[Block] = ArrayBuffer.empty[Block]
  var emptyPool = false
  implicit val ec: ExecutionContext = contexts.taskContext
  val templates: ArrayBuffer[UninitializedPool] = ArrayBuffer() ++ PoolTemplates.templates
  val creator: String = wallet.p2pk.toString

  if(taskConfig.enabled) {
    logger.info(db.source.toString)
    logger.info(dbConfig.profileName)
    logger.info(s"Pool Initialization will start in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>
        performTestnetSetup
        val incompleteTemps = Try {
          findIncompletePools
        }
        incompleteTemps.recoverWith{
          case ex =>
            logger.error("There was a critical error while checking processing blocks!", ex)
            Failure(ex)
        }
        if(incompleteTemps.isSuccess) {
          Try(createNextPool(incompleteTemps.get)).recoverWith {
            case ex =>
              logger.error("There was a critical error creating the next pool!", ex)
              Failure(ex)
          }
        }
    })(contexts.taskContext)
  }

  def createNextPool(incomplete: ArrayBuffer[UninitializedPool]) = {
    if(incomplete.nonEmpty) {
      val nextEmissions = incomplete.filter(i => i.poolMade && i.emissionsMade.isDefined && !i.emissionsMade.get)
      if (nextEmissions.nonEmpty) {
        logger.info("Now creating next emissions!")
        val info = db.run(Tables.PoolInfoTable.filter(_.title === nextEmissions.head.template.title).map(_.poolTag).result.head)
        info.map(i => logger.info(s"Found pool without emissions: $i"))
        info.map(i => createEmissions(i, nextEmissions.head.template.currency))
      } else {
        val nextPool = incomplete.head
        logger.info("Now creating next pool!")
        if (!nextPool.poolMade) {
          createPool(nextPool.template)
        }
      }
    }else{
      logger.info("All pools completed, now exiting initialization task...")
    }
  }

  def findIncompletePools: ArrayBuffer[UninitializedPool] = {
    logger.info("Getting incomplete pools...")
    val officialPools = Await.result(db.run(Tables.PoolInfoTable.filter(_.official === true).result), 60 seconds)
    val poolsToCheck = officialPools.filter(p => templates.exists(_.template.title == p.title))
    poolsToCheck.foreach{
      p =>
        logger.info(s"Found pool template with title ${p.title} in database with tag ${p.poolTag}")
        val poolTemplate = templates.find(_.template.title == p.title).get
        val tempIdx = templates.indexOf(poolTemplate)
        var nextTemplate = poolTemplate
        if(poolTemplate.template.emissionsType != PoolInformation.NoEmissions){
          if(p.emissions_id != PoolInformation.NoEmissions){
            logger.info(s"Template ${p.title} was found to have emissions id ${p.emissions_id}!")
            nextTemplate = nextTemplate.copy(emissionsMade = Some(true))
          }
        }
        nextTemplate = nextTemplate.copy(poolMade = true)
        templates(tempIdx) = nextTemplate
    }
    val incompleteTemps = templates.filter(t => !t.poolMade || (t.poolMade && t.emissionsMade.isDefined && !t.emissionsMade.get))
    logger.info(s"Template checks complete. There are a total ${incompleteTemps.length} incomplete templates.")
    incompleteTemps
  }


  def createPool(template: PoolTemplate) = {
    client.execute{
      ctx =>
        val empty   = new EmptySelector
        val builder = new GenesisBuilder(template.numSubpools, Helpers.MinFee, Some(template.tokenName), Some(template.tokenDesc))
        val pool    = new Pool(ArrayBuffer.empty[Subpool])
        val group   = new GenesisGroup(pool, ctx, wallet, Helpers.MinFee, (template.fee * PoolFees.POOL_FEE_CONST).toInt, template.feeOp)

        val groupManager = new GroupManager(group, builder, empty)
        groupManager.initiate()

        if(groupManager.isSuccess){
          val currentTime = LocalDateTime.now()
          val poolStates = for(subPool <- group.newPools)
            yield PoolState(subPool.token.toString, subPool.id, template.title, subPool.box.getId.toString, group.completedGroups.values.head.getId,
              0L, 0L, subPool.box.genesis, ctx.getHeight.toLong, PoolState.CONFIRMED, 0, 0L, template.feeOp.map(_.toString).getOrElse(creator), "none", 0L,
              currentTime, currentTime)
          logger.info("Creating new pool with tag " + poolStates.head.subpool)

          Future.sequence(Tables.makePoolPartitions(poolStates.head.subpool).map(db.run(_)))

          val infoInsert = Tables.PoolInfoTable += PoolInformation(group.newPools.head.token.toString, 0L, template.numSubpools, 0L, 0L, 0L, 0L,
            template.currency, PoolTemplates.getPaymentStr(template.paymentType), (template.fee * PoolFees.POOL_FEE_CONST).toLong, official = true,
            template.epochKick, template.maxMembers, template.title, template.feeOp.map(_.toString).getOrElse(creator), LocalDateTime.now(), LocalDateTime.now(), PoolInformation.NoEmissions,
            template.emissionsType)
          val stateInserts = Tables.PoolStatesTable ++= poolStates
          val infoRows = db.run(infoInsert)
          val stateRows = db.run(stateInserts)

          for(rows <- infoRows) yield logger.info(s"Partitions")
          stateRows.onComplete{
            case Success(value) =>
              logger.info(s"${value.toString} was the result of batch inserting pool states")
            case Failure(exception) =>
              logger.error("There was a fatal exception while insert pool states!", exception)
          }
        }else{
          logger.error(s"There was a critical error while creating pool ${template.title}!", groupManager.failedGroups.head._2)
        }
    }

  }

  def createEmissions(tag: String, currency: String) = {
    currency match {
      case PoolInformation.CURR_TEST_TOKENS =>
        EmissionsTransactions.makeTestTokenTx(tag)
      case PoolInformation.CURR_NETA =>
        val emMintBox = makeTokenTx(1L, "Phase 2 NETA Emission Box NFT", "Token representing the Phase 2 NETA Emission Box", 0, Helpers.MinFee*2)
        EmissionsTransactions.makeNetaEmissionTx(tag, emMintBox)
      case PoolInformation.CURR_ERG_COMET =>
        logger.info("Making emissions for COMET pool")
        val emMintBox = makeTokenTx(1L, "COMET Emission Box NFT", "Token representing the COMET Emission Box", 0, Helpers.MinFee*2)
        EmissionsTransactions.makeCometEmissionTx(tag, emMintBox)
       // makeTokenTx(1000000000000L, "tCOMET", "Testnet Comet", 0, Helpers.MinFee * 2)
    }
  }
  object EmissionsTransactions {
    def makeTestTokenTx(tag: String, emissionReward: Double = 67.5D) = {
        client.execute {
          ctx =>
            // TODO: Change creator in real token pools
            val fPoolCreator = db.run(Tables.PoolInfoTable.filter(_.poolTag === tag).map(_.creator).result.head)
            for(poolCreator <- fPoolCreator){
              val metadataContract = MetadataContract.generateMetadataContract(ctx)
              val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
              val emissionsContract = EmissionsContract.generate(ctx, wallet.p2pk, Address.create(poolCreator), holdingContract)


              val distributionToken = new ErgoToken(ErgoId.create("b7ba3da6a1d09c0ec73d655779bfbf933b5306002747b1c9eedcadee6b9e6994"), 10000 * Helpers.OneErg)

              val inputBoxes     = ctx.getCoveringBoxesFor(wallet.p2pk, Helpers.MinFee * 10, Seq().asJava).getBoxes
              val emissionsToken = new Eip4Token(inputBoxes.get(0).getId.toString, 1L, "GetBlok.io Token Emissions Test", "Test token identifying an emissions box", 0)

              val outBox     = ctx.newTxBuilder().outBoxBuilder()
                .contract(wallet.pk.contract)
                .value(Helpers.MinFee * 10)
                .mintToken(emissionsToken)
                .build()
              val unsignedTokenTx = ctx.newTxBuilder().boxesToSpend(inputBoxes).outputs(outBox).fee(Helpers.MinFee).sendChangeTo(wallet.p2pk.getErgoAddress).build()
              val signedTokenTx   = wallet.prover.sign(unsignedTokenTx)
              val tokenTxId       = ctx.sendTransaction(signedTokenTx).replace("\"", "")
              val tokenInputBox   = outBox.convertToInputWith(tokenTxId, 0.toShort)


              val tokenInputs = ctx.getCoveringBoxesFor(wallet.p2pk, Helpers.MinFee, Seq(distributionToken).asJava).getBoxes
                .asScala.toSeq.filter(i => i.getTokens.size() > 0).filter(i => i.getTokens.get(0).getId == distributionToken.getId)
              val emissionsOutBox = EmissionsContract.buildGenesisBox(ctx, emissionsContract,
                Helpers.ergToNanoErg(emissionReward), Address.create(poolCreator), emissionsToken.getId, distributionToken)
              val txB = ctx.newTxBuilder()
              val unsigned = txB.boxesToSpend((Seq(tokenInputBox)++tokenInputs).asJava)
                .outputs(emissionsOutBox)
                .fee(Helpers.MinFee)
                .sendChangeTo(wallet.p2pk.getErgoAddress)
                .build()
              val signed = wallet.prover.sign(unsigned)
              val txId = ctx.sendTransaction(signed)

              val emissionsQuery = for { info <- Tables.PoolInfoTable if info.poolTag === tag } yield info.emissionsId
              val updateEmissions = emissionsQuery.update(emissionsToken.getId.toString)
              db.run(updateEmissions)

          }
       }
    }

    def makeNetaEmissionTx(tag: String, emTokenMintBox: InputBox) = {
      Try {
        client.execute {
          ctx =>
            //TODO: Use exact box id here if necessary
            logger.info("Building transactions to create NETA Emissions Box")
            val metadataContract = MetadataContract.generateMetadataContract(ctx)
            val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
            val template = EmissionTemplates.getNETATemplate(ctx.getNetworkType)
            logger.info("Metadata and holding contracts generated")
            val emissionsContract = ExchangeContract.generate(ctx, wallet.p2pk, template.swapAddress, holdingContract, template.lpNFT, template.distToken)
            logger.info("Emissions Contract generated")
            logger.info(s"EmEx address: ${emissionsContract.getAddress}")
            Thread.sleep(100000L)
            val tokenInputs = ctx.getBoxesById("ed29641a2b8c896c47dac878132e9b75d86c4043f1816d3fba103938e86b93c8")
            val totalDistributionToken = tokenInputs.head.getTokens.get(0)
            logger.info(s"Token input boxes grabbed from chain. Num boxes: ${tokenInputs.length}")
            logger.info(s"Token inputs head: ${tokenInputs.head.toJson(true)}")
            val emissionsOutBox = ExchangeContract.buildGenesisBox(ctx, emissionsContract,
              template.initPercent, template.initFee, emTokenMintBox.getTokens.get(0).getId, totalDistributionToken)
            val txB = ctx.newTxBuilder()
            val unsigned = txB.boxesToSpend((Seq(emTokenMintBox) ++ Seq(tokenInputs.head)).asJava)
              .outputs(emissionsOutBox)
              .fee(Helpers.MinFee)
              .sendChangeTo(wallet.p2pk.getErgoAddress)
              .build()
            logger.info("Signing tx.")
            val signed = wallet.prover.sign(unsigned)
            logger.info("Transaction signed. Now sending transaction")
            val txId = ctx.sendTransaction(signed)
            logger.info("Transaction sent")
            val emissionsQuery = for {info <- Tables.PoolInfoTable if info.poolTag === tag} yield info.emissionsId
            val updateEmissions = emissionsQuery.update(emTokenMintBox.getTokens.get(0).getId.toString)
            db.run(updateEmissions)
            logger.info("Finished making NETA Emissions Box")
        }
      }.recoverWith{
        case e: Exception =>
          logger.error("A fatal exception occurred while making neta pool", e)
          Failure(e)
      }
    }

    def makeCometEmissionTx(tag: String, emTokenMintBox: InputBox) = {
      client.execute{
        ctx =>

          logger.info("Building transactions to create COMET Emissions Box")
          val metadataContract = MetadataContract.generateMetadataContract(ctx)
          val holdingContract = AdditiveHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
          val template = EmissionTemplates.getCOMETTemplate(ctx.getNetworkType)
          logger.info("Metadata and holding contracts generated")
          val emissionsContract = ProportionalEmissionsContract.generate(ctx, wallet.p2pk, template.swapAddress, holdingContract, template.distToken,
            template.decimalPlaces)
          logger.info("Emissions Contract generated")

          val totalDistributionToken = new ErgoToken(template.distToken, template.totalEmissions)
          val tokenInputs = ctx.getBoxesById("fbdeaacba434569f0412ae7a1b82b6f05e827847ce1e8fca8735d5f0bd740470")
          logger.info("Token input boxes grabbed from chain")
          val emissionsOutBox = ProportionalEmissionsContract.buildGenesisBox(ctx, emissionsContract,
            template.initProportion, template.initFee, emTokenMintBox.getTokens.get(0).getId, totalDistributionToken)
          val txB = ctx.newTxBuilder()
          val unsigned = txB.boxesToSpend((Seq(emTokenMintBox)++tokenInputs).asJava)
            .outputs(emissionsOutBox)
            .fee(Helpers.MinFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .build()
          logger.info("Signing tx.")
          val signed = wallet.prover.sign(unsigned)
          val txId = ctx.sendTransaction(signed)
          logger.info("Sending transaction")
          val emissionsQuery = for { info <- Tables.PoolInfoTable if info.poolTag === tag } yield info.emissionsId
          val updateEmissions = emissionsQuery.update(emTokenMintBox.getTokens.get(0).getId.toString)
          db.run(updateEmissions)
          logger.info("Finished making COMET Emissions Box")
      }
    }
  }

  def performTestnetSetup = {
    if(AppParameters.networkType == NetworkType.TESTNET){

     /* logger.info("Performing testnet setup...")
      val lpNFT = new ErgoToken(EmissionTemplates.NETA_TESTNET.lpToken, 1L)
      val lpTokens = new ErgoToken(ErgoId.create("4eab0718642b680f8bac258aec0c0b7edc5c2dc8bc0e7fac59103fb73947038f"), 100000)
      val tNETA   = new ErgoToken(EmissionTemplates.NETA_TESTNET.distToken, 14538197936805L)
      val totalValue = Helpers.ergToNanoErg(124262.460493556)
      makeFakeLPTx(totalValue, lpNFT, lpTokens, tNETA, 996, "14a8fb5d0f61af67482c7d4de9360627ab10d6bab962329d5a81aef34aeb7068")*/
    }
  }

  def makeTokenTx(amount: Long, name: String, desc: String, decimals: Int, outVal: Long): InputBox = {
    client.execute{
      ctx =>
        logger.info("Making NFT for comet emissions box")
        val inputBoxes     = ctx.getWallet.getUnspentBoxes(outVal + Helpers.MinFee).get()
        logger.info("Found boxes for NFT")
        val newToken = new Eip4Token(inputBoxes.get(0).getId.toString, amount, name, desc, decimals)
        logger.info("Building NFT minting transaction")
        val outBox = ctx.newTxBuilder().outBoxBuilder()
          .value(outVal)
          .tokens(newToken)
          .contract(wallet.contract)
          .registers(newToken.getMintingBoxR4, newToken.getMintingBoxR5, newToken.getMintingBoxR6)
          .build()
        val unsignedTx = ctx.newTxBuilder()
          .boxesToSpend(inputBoxes)
          .outputs(outBox)
          .fee(Helpers.MinFee)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()
        logger.info("Emissions NFT transaction built, now signing")
        val signed = wallet.prover.sign(unsignedTx)
        logger.info("Transaction signed, now sending.")
        val txId = ctx.sendTransaction(signed)
        logger.info("Now returning box as input")
        outBox.convertToInputWith(txId.replace("\"", ""), 0)
    }

  }


  val sigmaTrueScript =
    """
      |{
      |  val x = Coll(0)
      |  val y = Coll(0,1)
      |
      |  val dummyValue: Boolean = x == y
      |  sigmaProp(dummyValue || true)
      |}
      |""".stripMargin
  def spendLPBox() = {
    client.execute{
      ctx =>
        val multiplier1 = -1
        val sigmaTrueContract = ctx.compileContract(ConstantsBuilder.create().build(), sigmaTrueScript)
        val lpBox = ctx.getCoveringBoxesFor(sigmaTrueContract.toAddress, Helpers.MinFee, Seq().asJava).getBoxes.get(0)
        val multiplier2 = -1
        val randomValue = Math.random() * Helpers.OneErg * 50 * multiplier1
        val randomToken = Math.random() * 100000000L * 50 * multiplier2

        val nextLpBox = {
          ctx.newTxBuilder().outBoxBuilder()
            .value(lpBox.getValue + randomValue.toLong - Helpers.MinFee)
            .registers(lpBox.getRegisters.asScala.toSeq:_*)
            .tokens(lpBox.getTokens.get(0), lpBox.getTokens.get(1),
              new ErgoToken(lpBox.getTokens.get(2).getId, lpBox.getTokens.get(2).getValue + randomToken.toLong))
            .contract(sigmaTrueContract)
            .build()
        }

        val uTx = ctx.newTxBuilder()
          .boxesToSpend(Seq(lpBox).asJava)
          .outputs(nextLpBox)
          .fee(Helpers.MinFee)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()
        val sTx = wallet.prover.sign(uTx)
        val txId = ctx.sendTransaction(sTx)
        logger.info(s"Sent an lp spending transaction with id $txId")
    }

  }
  def makeFakeLPTx(ergAmount: Long, lpNFT: ErgoToken, lpToken: ErgoToken, distToken: ErgoToken, feeNum: Int, bigBoxId: String) = {


    client.execute{
      ctx =>
        logger.info("Making Fake LP Box")
        val tokenSeq = Seq(lpNFT, lpToken, distToken)
        logger.info("Collecting boxes")
        val inputBoxes     = ctx.getCoveringBoxesFor(wallet.p2pk, 0L, tokenSeq.asJava).getBoxes.asScala.toSeq
        logger.info("Compiling contract")
        val sigmaTrueContract = ctx.compileContract(ConstantsBuilder.create().build(), sigmaTrueScript)
        logger.info(s"sigmaTrue: ${sigmaTrueContract.toAddress.toString}")
        val boxesToSpend = ctx.getBoxesById(bigBoxId) ++ inputBoxes
        val outBox = ctx.newTxBuilder().outBoxBuilder()
          .value(ergAmount)
          .tokens(tokenSeq:_*)
          .contract(sigmaTrueContract)
          .registers(ErgoValue.of(feeNum))
          .build()
        val unsignedTx = ctx.newTxBuilder()
          .boxesToSpend(boxesToSpend.toSeq.asJava)
          .outputs(outBox)
          .fee(Helpers.MinFee)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()
        logger.info("Tx built, now signing")
        val signed = wallet.prover.sign(unsignedTx)
        val txId = ctx.sendTransaction(signed)
        logger.info(s"Sent LP box transaction with tx id:  ${txId}")
        txId
    }

  }
}
