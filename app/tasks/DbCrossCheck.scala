package tasks

import actors.BlockingDbWriter._
import actors.ExplorerRequestBus.ExplorerRequests.{BoxesById, BoxesByTokenId, FatalExplorerError, TimeoutError, TxById}
import actors.QuickDbReader.{PaidAtGEpoch, PlacementsByBlock, PoolBlocksByStatus, PoolMembersByGEpoch, QueryAllSubPools, QueryPoolInfo}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.google.common.primitives.Longs
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ExplorerConfig, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.explorer.Models.{Output, RegisterData, TransactionData}
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{Block, PoolBlock, PoolInformation, PoolMember, PoolPlacement, PoolState, Share}
import io.getblok.subpooling_core.plasma.StateConversions.{balanceConversion, dualBalanceConversion}
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, PartialStateMiner, SingleBalance, StateBalance}
import io.getblok.subpooling_core.registers.PropBytes
import models.DatabaseModels.{Balance, BalanceChange, ChangeKeys, Payment, SPoolBlock, StateHistory}
import models.ResponseModels.writesChangeKeys
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoId, ErgoValue, InputBox, NetworkType}
import persistence.Tables
import plasma_utils.{EmissionValidator, TransformValidator}
import plasma_utils.payments.PaymentRouter
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsResult, JsValue, Json, Reads}
import play.api.libs.mailer.Email
import play.api.{Configuration, Logger}
import play.db.NamedDatabase
import play.libs.mailer.MailerClient
import scorex.crypto.authds.{ADKey, ADValue}
import scorex.crypto.authds.avltree.batch.Insert
import sigmastate.BoolToSigmaProp
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.lifted.ExtensionMethods
import special.collection.Coll
import special.sigma.AvlTree
import utils.ConcurrentBoxLoader

import java.io.File
import java.time.{Instant, LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{higherKinds, postfixOps}
import scala.util.{Failure, Success, Try}

@Singleton
class DbCrossCheck @Inject()(system: ActorSystem, config: Configuration,
                                    @Named("quick-db-reader") query: ActorRef,
                                    @Named("blocking-db-writer") write: ActorRef,
                                    @Named("explorer-req-bus") expReq: ActorRef,
                                    mailerClient: MailerClient,
                                    protected val dbConfigProvider: DatabaseConfigProvider)
                                    extends HasDatabaseConfigProvider[PostgresProfile]{

  import dbConfig.profile.api._
  val logger: Logger = Logger("DatabaseCrossCheck")
  val taskConfig: TaskConfiguration = new TasksConfig(config).dbCrossCheckConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val expConfig = new ExplorerConfig(config)
  val ergoClient: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)
  var blockQueue: ArrayBuffer[Block] = ArrayBuffer.empty[Block]
  final val REGEN_DISTS  = "dists"
  final val REGEN_PLACES = "places"
  final val REGEN_STATES = "states"
  final val REGEN_STORED = "stored"
  implicit val ec: ExecutionContext = contexts.taskContext
  if(taskConfig.enabled) {
    logger.info(db.source.toString)
    logger.info(dbConfig.profileName)
    logger.info(s"DatabaseCrossCheck will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>
        if(!params.regenFromChain) {
          Try {
            checkProcessingBlocks
          }.recoverWith {
            case ex =>
              logger.error("There was a critical error while checking processing blocks!", ex)
              Failure(ex)
          }
          Try(
            checkDistributions
          ).recoverWith {
            case ex =>
              logger.error("There was a critical error while checking distributions!", ex)
              Failure(ex)
          }
          Try {
            val transformValidator = new TransformValidator(expReq, contexts, params, db)
            transformValidator.checkInitiated()
          }.recoverWith{
            case ex =>
              logger.error("There was a critical error while validating transforms!", ex)
              Failure(ex)
          }
          Try {
            val transformValidator = new EmissionValidator(expReq, contexts, params, db)
            transformValidator.checkProcessing()
          }.recoverWith{
            case ex =>
              logger.error("There was a critical error while validating emissions!", ex)
              Failure(ex)
          }

        }else {
          logger.info(s"Regen from chain was enabled, now regenerating digest state for pool" +
            s" ${params.regenStatePool}")
          //regenHistory()
          chooseBackupType(params.regenStatePool)
//          Try(execRegen(params.regenType)).recoverWith {
//            case ex =>
//              logger.error("There was a critical error while re-generating dbs!", ex)
//              Failure(ex)
//          }


        }
    })(contexts.taskContext)
  }

  case class ContextExtension(extZero: String)
  def parseExtension(json: String) = {
    ContextExtension(json.split(":")(1).split("\"")(1))
  }


  def grabBox(boxId: String): Try[InputBox] = {
    Try {
      ergoClient.execute {
        ctx =>
          ctx.getBoxesById(boxId).head
      }
    }
  }


  def performSyncCheck() = {
    val plasmaPayers = Seq(PoolInformation.PAY_PLASMA_SOLO, PoolInformation.PAY_PLASMA_PPLNS)
    val pools = Await.result(db.run(Tables.PoolInfoTable.result), 20 seconds)
    val poolStates = Await.result(db.run(Tables.PoolStatesTable.result), 20 seconds)
    val plasmaInfos = pools.filter(i => plasmaPayers.contains(i.payment_type))

    for(info <- plasmaInfos){
      val balState = PaymentRouter.routeBalanceState(info)
      val poolState = poolStates.filter(ps => ps.subpool == info.poolTag).head

      if(poolState.status != PoolState.SUCCESS){
        val box = grabBox(poolState.box)

        box match {
          case Success(poolBox) =>
            val digestString = Hex.toHexString(poolBox.getRegisters.get(0).getValue.asInstanceOf[AvlTree].digest.toArray)
            val localDigest = balState.map.toString()

            if(localDigest != digestString) {
              logger.warn(s"Pool ${poolState.subpool} is unsynced with local digest ${localDigest} and on-chain digest ${digestString}")

            } else
              logger.info("")

          case Failure(e: Throwable) =>
            logger.error(s"Subpool state box for pool ${poolState.subpool} is unsynced!")

        }
      }

    }
  }



  def regenHistory() = {
    val poolTag = "f0f3581ea3aacf37c819f0f18a47585866aaf4c273d0c3c189d79b7d5fc71e80"
    val gEpoch = 284L
    val success = "success"
    val time = LocalDateTime.now()
    val none = "none"
    val block = 842388L
    val history = Seq(
      StateHistory(poolTag, gEpoch, "e4b81bfd8ed3b53fd13c369f76790c03cbbb2e4a9e7169a9294cd58a9036a548",
        "f8c66ca8f431e490dfb99d0380b0b25cf97b58666d6964171bd9545415602f33", "140bfa64407ba783b076be3678595d9bc2b70f91ad11375cf547f1459fb1507c",
        "INSERT", success, 0, "2c46bcea2860dcb3dcd5814be7e5ce2d443092f8ef883a95db823b4d36223a410c", none, none, none, none, none,
        block, time, time),
      StateHistory(poolTag, gEpoch, "896e534ea39dbd124f98410acb4a362a34f8e8b00af3fcb4031162b04a99eac4",
        "4390726a0b46a7994f7d873d29509a5315afab9ed080103613f8479d2b89f2eb", "8ee179db997fc1f04a46bc6c0a3df6e1afe6b27308785e9e40aa2eec65a218de",
        "UPDATE", success, 1, "697e188b2fa43e71759a469f96e33e5694a646a5d402e7279b68034accd5be000c", none, none, none, none, none,
        block, time, time),
      StateHistory(poolTag, gEpoch, "e0cc324c73a899d59971f71c28afc841d70502b02bd0bcf5810c64bfba2e01ba",
        "c4d722d9984f96f94375914a1383b5c0940d464ef51c4c30867d30e2d02265c3", "4e6010f9d78401e54aa5248ca67cb5aa1914569058ed8b8bb4cc51927ff11fc6",
        "UPDATE", success, 2, "925e02504edbd073790800e9ccde8f301aec8682cf2d553408e542f4e8d0c2490c", none, none, none, none, none,
        block, time, time),
      StateHistory(poolTag, gEpoch, "a19a734e3c6b26e101a82a14767ac7f9687ea7301817384d59b240cda41405dd",
        "30959a8883249d372dd13dca45d5086e0bcc819422dd7733bc62d3d4a81eb120", "e513f5c31523392e4b130531b05fc2972a6352398beb1df9b8bc5f8cb4cc754c",
        "PAYOUT", success, 3, "3bc573feef6bf91e7947eda65954b2a9b69aa22caa2c86d8e2f53d576ec588bd0c", none, none, none, none, none,
        block, time, time),

    )
    db.run(Tables.StateHistoryTables ++= history)
//    db.run(Tables.PoolBlocksTable.filter(_.blockHeight === block).map(_.status).update(PoolBlock.PAID))
//    db.run(Tables.PoolInfoTable.filter(_.poolTag === poolTag).map(i => i.gEpoch -> i.updated)
//      .update(gEpoch -> LocalDateTime.now()))
//    db.run(Tables.PoolStatesTable.filter(s => s.subpool === poolTag).map{
//      s => (s.tx, s.epoch, s.height, s.status, s.block, s.updated)
//    }.update("7f8d16238865dbc6cacd5241424bf1d912f639002cddac427ebdc653057e480b", gEpoch, 802418L, "confirmed", block, LocalDateTime.now()))
//
//    val outputs = db.run(Tables.NodeOutputsTable
//      .filter(_.txId === "7f8d16238865dbc6cacd5241424bf1d912f639002cddac427ebdc653057e480b")
//      .filterNot(_.address === "6ioi264iGHooExShvfCDyu7ar4PEzStvf61DWqf2PLUqM5bXff7sbP4T4X5fczBxijBawTb3oyza22EmTu7z5C6TB3bu9AJ1bP24BDTm2GbjHDxrbaN4P9Gy83yZWUdT8wEvUsWLs5wWNsLF68GCoWe3UnW8C2Xs5wZEWVaXcJJkRHAq9zLqZDZTMcko6zLGQjj55g3RkCjZUQ8WU7nsnXdGtxoPG1baTQ6m6DJK1GAy8SSRpJE9DaGNn749T68PJuMDdHNJvBU9JGHcKyDQBDwGYkKrZMLBr")
//      .filterNot(_.address === "2iHkR7CWvD1R4j1yZg5bkeDRQavjAaVPeTDFGGLZduHyfWMuYpmhHocX8GJoaieTx78FntzJbCBVL6rf96ocJoZdmWBL2fci7NqWgAirppPQmZ7fN9V6z13Ay6brPriBKYqLp1bT2Fk4FkFLCfdPpe")
//      .result
//    )
//    outputs.map{
//      outs =>
//        val payments = outs.map{
//          o =>
//            Payment(poolTag, o.address, "ERG", Helpers.convertFromWhole("ERG", o.value),
//              o.txId, None, LocalDateTime.now(), block, gEpoch)
//        }
//        db.run(Tables.Payments ++= payments)
//    }
  }
  def chooseBackupType(poolTag: String) = {
    poolTag match {
      case "f0f3581ea3aacf37c819f0f18a47585866aaf4c273d0c3c189d79b7d5fc71e80" =>
        initSingleBackup(poolTag)
      case "11c61b7f33860116201ad58ce1d08117eafd26019045ff373148b32229bc6ac9" =>
        initSingleBackup(poolTag)
      case "3bfc08ec8f536039ae2e19afb5cbaf6329f12b4bf84cdd0a8de5f7a023ad1f43" =>
        initSingleBackup(poolTag)
      case "198999881b270fa41546ba3fb339d24c24914fbbf11a8283e4c879d6e30770b0" =>
        initDualBackup(poolTag)
      case _ =>
        logger.error("An invalid pool tag was given for re-syncing!")
    }
  }

  def initDualBackup(poolTag: String) = {
    new File(AppParameters.plasmaStoragePath + s"/backup").mkdir()
    val balanceState = new BalanceState[DualBalance]("backup")

    syncDualState(balanceState, poolTag).map {
      _ =>
        logger.info(s"Old state digest: ${balanceState.map.toString()}")
        balanceState.map.commitChanges()
        logger.info(s"New state digest: ${balanceState.map.toString()}")
    }
  }

  def initSingleBackup(poolTag: String) = {
    new File(AppParameters.plasmaStoragePath + s"/backup").mkdir()
    val balanceState = new BalanceState[SingleBalance]("backup")

    syncSingleState(balanceState, poolTag).map {
      _ =>
        logger.info(s"Old state digest: ${balanceState.map.toString()}")
        balanceState.map.commitChanges()
        logger.info(s"New state digest: ${balanceState.map.toString()}")
    }
  }

  def syncSingleState(balanceState: BalanceState[SingleBalance], poolTag: String) = {

    logger.info(s"Balance state has initial digest ${balanceState.map.toString()}")

    val fStateHistory = db.run(Tables.StateHistoryTables.filter(_.poolTag === poolTag)
      .sortBy(_.created).result) // TODO: Make this per pool
    fStateHistory.map{
      stateHistory =>
        balanceState.map.initiate()
        val historyGrouped = stateHistory.groupBy(_.gEpoch).map(h => h._1 -> h._2.sortBy(sh => sh.step)).toArray.sortBy(_._1)


        historyGrouped.foreach{
          historyPair =>
            val historySteps = historyPair._2
            logger.info(s"Checking history steps for gEpoch ${historyPair._1}")
            historySteps.foreach{
              step =>
                logger.info("Now parsing steps")
                if(step.step != -1) {
                  val boxExtension = Await.result(db.run(Tables.NodeInputsTable.filter(_.boxId === step.commandBox).map(_.extension).result.head), 1000 seconds)
                  val ext = parseExtension(boxExtension)
                  logger.info("Extension parsed successfully!")
                  logger.info("Now parsing into ErgoValue")
                  val ergoVal = ErgoValue.fromHex(ext.extZero).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
                  val asArr = ergoVal.toArray.map(c => c._1.toArray -> c._2.toArray)
                  logger.info(s"Now performing step ${step.step} with command ${step.command} for gEpoch ${step.gEpoch}," +
                    s"and expected digest ${step.digest}")
                  val nextDigest = Try(performSingleCommand(balanceState, asArr, step.command)).recoverWith{
                    case e: Exception =>
                      logger.error("There was a fatal error performing a command!", e)
                      Failure(e)
                  }
                  logger.info(s"New digest after command: ${nextDigest}")
                  logger.info(s"Expected digest after command ${step.digest}")
                }else{
                  logger.info(s"Skipping ${step.command} transform for gEpoch ${step.gEpoch}")
                }
              }

        }
    }
  }

  def performSingleCommand(balanceState: BalanceState[SingleBalance], commandBytes: Array[(Array[Byte], Array[Byte])], command: String) = {
    command match{
      case "INSERT" =>
        val keys = commandBytes.map(_._1).map(PartialStateMiner.apply)
        val updates = keys.map{
          k =>
            k -> SingleBalance(0L)
        }
        balanceState.map.insert(updates: _*)
      case "UPDATE" =>
        val keys = commandBytes.map(_._1).map(PartialStateMiner.apply)
        val additions = commandBytes.map(_._2).map(Longs.fromByteArray).map(SingleBalance.apply)
        val lookup    = balanceState.map.lookUp(keys:_*)

        val updates = lookup.response.indices.map{
          idx =>
            val currBalance = lookup.response(idx).tryOp.get.get
            val nextBalance = SingleBalance(additions(idx).balance + currBalance.balance)

            val key = keys(idx)

            key -> nextBalance
        }
        balanceState.map.update(updates: _*)

      case "PAYOUT" =>
        val keys = commandBytes.map(_._1).map(PartialStateMiner.apply)
        val updates = keys.map{
          k =>
            k -> SingleBalance(0L)
        }
        balanceState.map.update(updates: _*)
    }
    balanceState.map.digestStrings._2.get
  }



  def applyDualStep(balanceState: BalanceState[DualBalance], step: StateHistory) = {
    val boxExtension = Await.result(db.run(Tables.NodeInputsTable.filter(_.boxId === step.commandBox).map(_.extension).result.head), 1000 seconds)
    val ext = parseExtension(boxExtension)
    logger.info("Extension parsed successfully!")
    logger.info("Now parsing into ErgoValue")
    val ergoVal = ErgoValue.fromHex(ext.extZero).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
    val asArr = ergoVal.toArray.map(c => c._1.toArray -> c._2.toArray)
    logger.info(s"Now performing step ${step.step} with command ${step.command} for gEpoch ${step.gEpoch}," +
      s"and expected digest ${step.digest}")
    val nextDigest = Try(performDualCommand(balanceState, asArr, step.command)).recoverWith{
      case e: Exception =>
        logger.error("There was a fatal error performing a command!", e)
        Failure(e)
    }
    logger.info(s"New digest after command: ${nextDigest}")
    logger.info(s"Expected digest after command ${step.digest}")
  }

//  def getNextTx(txInfo: TransactionData): Option[TransactionData] = {
//    val spendingId = txInfo.outputs.head.spendingTxId
//    spendingId.flatMap {
//      spId =>
//        val lastTx = expConfig.explorerHandler.getTransaction(spId)
//        lastTx
//    }
//  }
//
//  def syncTx(txInfo: TransactionData) = {
//    if(txInfo.inputs.size > 1) {
//      val commandType = txInfo.inputs(1).address.toString
//
//      val commandString = commandType match {
//        case "" =>
//
//      }
//    }
//  }
//
//  def getPrevTx(poolTag: String, txInfo: TransactionData): Option[TransactionData] = {
//    val lastTx = expConfig.explorerHandler.getTransaction(txInfo.inputs.head.creationTxId)
//    if(lastTx.isDefined) {
//        if(lastTx.get.inputs.head.assets.headOption.exists(_.id.toString == poolTag)){
//          lastTx
//        }else{
//          None
//        }
//    }else{
//      None
//    }
//
//  }
//
//  def getFirstTx(poolTag: String): Option[TransactionData] = {
//    val boxes = expConfig.explorerHandler.boxesByTokenId(ErgoId.create(poolTag))
//    if(boxes.isDefined){
//      val initTxId = boxes.get.head.txId
//      val initTx = expConfig.explorerHandler.getTransaction(initTx)
//      var prevTx = getPrevTx(poolTag, initTx.get)
//      while(prevTx.isDefined){
//        prevTx = getPrevTx(poolTag, prevTx.get)
//      }
//      prevTx
//    }else{
//      throw new Exception("Could not find boxes!!!")
//    }
//  }

  def syncDualState(balanceState: BalanceState[DualBalance], poolTag: String) = {

    logger.info(s"Balance state has initial digest ${balanceState.map.toString()}")

    val fStateHistory = db.run(Tables.StateHistoryTables.filter(_.poolTag === poolTag)
      .sortBy(_.created).result) // TODO: Make this per pool
    fStateHistory.map{
      stateHistory =>
        balanceState.map.initiate()
        val historyGrouped = stateHistory.groupBy(_.gEpoch).map(h => h._1 -> h._2.sortBy(sh => sh.step)).toArray.sortBy(_._1)


        historyGrouped.foreach{
          historyPair =>
            val historySteps = historyPair._2
            logger.info(s"Checking history steps for gEpoch ${historyPair._1}")
            historySteps.foreach{
              step =>
                logger.info("Now parsing steps")
                  applyDualStep(balanceState, step)

            }

        }
    }
  }

  def performDualCommand(balanceState: BalanceState[DualBalance], commandBytes: Array[(Array[Byte], Array[Byte])], command: String) = {
    command match{
      case "INSERT" =>
        val keys = commandBytes.map(_._1).map(PartialStateMiner.apply)
        val updates = keys.map{
          k =>
            k -> DualBalance(0L, 0L)
        }
        balanceState.map.insert(updates: _*)
      case "UPDATE" =>
        val keys = commandBytes.map(_._1).map(PartialStateMiner.apply)
        val additions = commandBytes.map(_._2).map(b => DualBalance(Longs.fromByteArray(b.slice(0, 8)), Longs.fromByteArray(b.slice(8, 16))))
        val lookup    = balanceState.map.lookUp(keys:_*)
        val zipped = keys.zip(lookup.response).zip(additions)
        val updates = zipped.filter(t => t._1._2.tryOp.isSuccess && t._1._2.tryOp.get.isDefined).map{
          t =>
            val currBalance = t._1._2.tryOp.get.get
            val addition = t._2
            val nextBalance = DualBalance(addition.balance + currBalance.balance, addition.balanceTwo + currBalance.balanceTwo)

            val key = t._1._1

            key -> nextBalance
        }
        balanceState.map.update(updates: _*)

      case "PAYOUT" =>
        val keys = commandBytes.map(_._1).map(PartialStateMiner.apply)
        val updates = keys.map{
          k =>
            k -> DualBalance(0L, 0L)
        }
        balanceState.map.update(updates: _*)
    }
    balanceState.map.digestStrings._2.get
  }



  def cleanDB = {
    db.run(Tables.PoolBlocksTable.filter(b => b.gEpoch === 16L && b.poolTag === "30afb371a30d30f3d1180fbaf51440b9fa259b5d3b65fe2ddc988ab1e2a408e7").map(_.status).update(PoolBlock.PROCESSED))
    db.run(Tables.SubPoolMembers.filter(b => b.g_epoch === 16L && b.subpool === "30afb371a30d30f3d1180fbaf51440b9fa259b5d3b65fe2ddc988ab1e2a408e7").delete)

  }
  def initEIP27 = {
    val fBlocks = db.run(Tables.PoolBlocksTable.filter(b => b.blockHeight >= 777217L && b.reward >= 63.0D).result)
    fBlocks.map{
      blocks =>
        val blockUpdates = blocks.map(b => b.blockheight -> (b.reward - 12.0D))
        blockUpdates.foreach{
          b =>
            db.run(Tables.PoolBlocksTable.filter(pb => pb.blockHeight === b._1).map(pb => pb.reward).update(b._2))
        }
    }
  }
  def execRegen(rType: String): Unit = {
    rType match {
      case REGEN_DISTS =>
        regenerateDB
      case REGEN_PLACES =>
        regeneratePlaces
      case REGEN_STATES =>
        regenStates
      case REGEN_STORED =>
        regenStored
    }
    ()
  }

  // TODO: Parameterize pools for regen and restarts
  def regenStored = {
    implicit val timeout: Timeout = Timeout(100 seconds)
    val states = Await.result(db.run(Tables.PoolStatesTable.filter(_.subpool === "30afb371a30d30f3d1180fbaf51440b9fa259b5d3b65fe2ddc988ab1e2a408e7").result), 1000 seconds)
    val outputs = Await.result(Future.sequence(states.map(s => (expReq ? BoxesById(ErgoId.create(s.box))).mapTo[Option[Output]])), 1000 seconds)
    val currentOutputs = outputs.filter(o => o.isDefined).map(_.get)
    val currentTxs = Await.result(Future.sequence(currentOutputs.map(so => (expReq ? TxById(so.txId)).mapTo[Option[TransactionData]])), 1000 seconds)
      .filter(_.isDefined).map(_.get).filter(_.id.toString != "d07c519ad591cfdc895862f51c9fa43568866a445edd751548b80f422a43a6c2")

    states.foreach{
      state =>
        val tx = currentTxs.find(t => t.outputs.map(o => o.id.toString).contains(state.box)).get
        val storedId = tx.outputs.find(o => o.id.toString == tx.inputs(2).id.toString).map(_.id.toString).getOrElse("none")
        val storedVal = tx.outputs.find(o => o.id.toString == tx.inputs(2).id.toString).map(_.assets.head.amount).getOrElse(0L)
        db.run(Tables.PoolStatesTable.filter(s => s.subpool_id === state.subpool_id && s.subpool === state.subpool)
          .map(s => s.storedId -> s.storedVal).update(storedId -> storedVal))
        logger.info(s"Updated state ${state.subpool_id} with stored id ${storedId} and val ${storedVal}")
    }
  }

  def restartPlaces = {
    val fBlocks = db.run(Tables.PoolBlocksTable.filter(_.poolTag === "b242eab6b734dd8da70b37a5f70f40f392af401f5971b6b36815bf28b26b128b")
      .filter(_.status === PoolBlock.PROCESSING).sortBy(_.gEpoch).result)
    fBlocks.map{
      blocks =>

        db.run(Tables.PoolBlocksTable
          .filter(b => b.poolTag === blocks.head.poolTag)
          .filter(b => b.gEpoch >= blocks.head.gEpoch && b.gEpoch <= blocks.last.gEpoch)
          .map(b => b.status -> b.updated)
          .update(PoolBlock.PRE_PROCESSED, LocalDateTime.now()))
    }

  }

  def cleanDupBlocks = {
    implicit val timeout: Timeout = Timeout(100 seconds)
    val fBlocks = db.run(Tables.PoolBlocksTable.filter(b => b.blockHeight === 780182L && b.nonce === "229a0003db8a1034").result)
    fBlocks.map{
      b =>
        db.run(Tables.PoolBlocksTable.filter(b => b.blockHeight === 780182L && b.nonce === "229a0003db8a1034").delete)
        db.run(Tables.PoolBlocksTable += b.head)
    }
  }

  def resetBlock = {
    db.run(Tables.PoolBlocksTable.filter(b => b.blockHeight === 780182L).map(b => b.gEpoch).update(304L))
  }

  def regenerateDB = {
    implicit val timeout: Timeout = Timeout(100 seconds)
    // TODO: UNCOMMENT DB CHANGES AND SET STATUS BACK TO PROCESSED

    val qBlock = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PROCESSED).filter(_.poolTag === "30afb371a30d30f3d1180fbaf51440b9fa259b5d3b65fe2ddc988ab1e2a408e7").sortBy(_.created).take(1).result.headOption)
    qBlock.map{
      block =>
        if(block.isDefined) {
          val states = Await.result(db.run(Tables.PoolStatesTable.filter(_.subpool === block.get.poolTag).result), 1000 seconds)
          val placements = Await.result(db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === block.get.poolTag && p.block === block.get.blockheight).result),
            1000 seconds)
          logger.info(s"Using block ${block.get.blockheight} with gEpoch ${block.get.gEpoch} for pool ${block.get.poolTag}")
          logger.info("Querying outputs from chain")
          val outputs = Await.result(Future.sequence(states.map(s => (expReq ? BoxesById(ErgoId.create(s.box))).mapTo[Option[Output]])), 1000 seconds)
          logger.info("Finished querying outputs, now querying transactions!")
          val spentOutputs = outputs.filter(_.isDefined).filter(_.get.spendingTxId.isDefined).map(_.get)
          val spendingTxs = Await.result(Future.sequence(spentOutputs.map(so => (expReq ? TxById(so.spendingTxId.get)).mapTo[Option[TransactionData]])), 1000 seconds)
            .filter(_.isDefined).map(_.get)
          logger.info("Finished querying spending txs from chain!")
          val totalPoolScore = placements.map(_.score).sum
          spendingTxs.foreach{
            tx =>
              val inState = tx.inputs.head
              val outState = tx.outputs.head
              logger.info(s"Getting box with id ${outState.id} from chain for tx ${tx.id}")
              val metadataBox = ergoClient.execute{ctx => new MetadataInputBox(ctx.getBoxesById(outState.id.toString)(0), ErgoId.create(block.get.poolTag))}
              logger.info("Finished querying box from chain!")
              logger.info("Metadata box found: ")
              logger.info(s"${metadataBox.asInput.toJson(true)}")
              val oldState = states.find(ps => ps.subpool_id == metadataBox.subpool)
              logger.info(s"Found old state ${oldState}")
              val samePlacements = placements.filter(_.subpool_id == metadataBox.subpool)
              logger.info(s"Found ${placements.length} placements. Using ${samePlacements.length} placements for" +
                s" subpool ${metadataBox.subpool}")
              val currTxId = tx.id.toString
              logger.info("Getting block")
              val currBlock = block.get
              logger.info("Making new state")
              val newState = oldState.get.copy(tx = currTxId, box = metadataBox.getId.toString, g_epoch = currBlock.gEpoch, epoch = metadataBox.epoch,
                  height = metadataBox.epochHeight, status = PoolState.SUCCESS, members = metadataBox.shareDistribution.size,
                  block = block.get.blockheight, updated = LocalDateTime.now())

              logger.info("Now updating state!")
              db.run(Tables.PoolStatesTable.filter(p => p.subpool === currBlock.poolTag && p.subpool_id === metadataBox.subpool)
                .map(s => (s.tx, s.box, s.gEpoch, s.epoch, s.height, s.status, s.members, s.block, s.storedId, s.storedVal, s.updated))
                .update((currTxId, metadataBox.getId.toString, currBlock.gEpoch, metadataBox.epoch, metadataBox.epochHeight,
                  PoolState.SUCCESS, metadataBox.shareDistribution.size, block.get.blockheight,
                  tx.outputs.find(o => o.address.toString == tx.inputs(2).address.toString).map(_.id.toString).getOrElse("none"),
                  tx.outputs.find(o => o.address.toString == tx.inputs(2).address.toString).map(o => o.assets.head.amount).getOrElse(0L),
                  LocalDateTime.now())))


              logger.info(s"New state: ${newState.toString}")
              logger.info("Now making next members")
              val nextMembers = samePlacements.map{
                p =>
                  logger.info(s"Evaluating miner ${p.miner} with placement ${p}")
                  val sharePerc = (BigDecimal(p.score) / totalPoolScore).toDouble
                  logger.info(s"sharePercent: ${sharePerc}")
                  val shareNum  = ((p.score * currBlock.netDiff) / AppParameters.scoreAdjustmentCoeff).toLong
                  logger.info(s"shareNum: ${shareNum}")
                  logger.info("Now finding miner in shareDist")
                  val distValue = metadataBox.shareDistribution.dist.find(_._1.address.toString == p.miner).get
                  logger.info("Miner found, now checking if miner had a payment in outputs")
                  val optPaid = tx.outputs.find(_.address.toString == p.miner).map(_.assets.head.amount)

                  logger.info(s"Miner payment: ${optPaid}")
                  logger.info(s"Now making new pool member for miner ${p.miner}")
                  // TODO: Make this more robust, and remember to remove old tokens in non token pools that are regened.
                  val member = PoolMember(currBlock.poolTag, metadataBox.subpool, currTxId, metadataBox.getId.toString, currBlock.gEpoch,
                    metadataBox.epoch, metadataBox.epochHeight, p.miner, p.score, shareNum, sharePerc, p.minpay, distValue._2.getStored,
                    optPaid.getOrElse(0L), p.amount, p.epochs_mined, "none", 0L, currBlock.blockheight, LocalDateTime.now())
                  logger.info("Finished making miner!")
                  member
              }

              logger.info("Next State: ")
              logger.info(newState.toString)
              logger.info(s"Next Members for ${newState.subpool_id}: ")
              nextMembers.foreach{
                m =>
                  logger.info(m.toString)
              }

              logger.info("Now updating state!")
              db.run(Tables.PoolStatesTable.filter(p => p.subpool === currBlock.poolTag).map(_.gEpoch).update(currBlock.gEpoch))
              logger.info("Now adding next members")
           //   db.run(Tables.SubPoolMembers ++= nextMembers)
              logger.info("Now updating gEpoch for all states")
              db.run(Tables.PoolInfoTable.filter(_.poolTag === currBlock.poolTag).map(i => i.gEpoch -> i.updated)
                .update(currBlock.gEpoch -> LocalDateTime.now()))
              logger.info("Now setting block to initiated status")
              db.run(Tables.PoolBlocksTable.filter(_.blockHeight === currBlock.blockheight).map(b => b.status -> b.updated)
                .update(PoolBlock.INITIATED -> LocalDateTime.now()))
              logger.info("Finished db updates!")
          }
        }
    }
  }

  def regeneratePlaces = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    logger.info("Regening placements")
    val placements = Await.result(db.run(Tables.PoolPlacementsTable.filter(_.block === params.regenPlaceBlock.toLong ).result), 1000 seconds)
    val poolPlaces = placements.groupBy(p => p.subpool).map(p => p._1 -> p._2.sortBy(s => s.subpool_id))
    for(poolPlace <- poolPlaces){
      val poolTag = "30afb371a30d30f3d1180fbaf51440b9fa259b5d3b65fe2ddc988ab1e2a408e7"
      if(poolPlace._1 == poolTag){
        val fTx = (expReq ? TxById(ErgoId.create(params.regenPlaceTx))).mapTo[Option[TransactionData]]
        fTx.map{
          optTx =>
            if(optTx.isDefined) {
              logger.info(s"Using tx ${optTx}")
              val tx = optTx.get
              logger.info("Now making next updates")
              val nextUpdates = poolPlace._2.map(p => p.subpool_id -> (tx.outputs(p.subpool_id.toInt).id.toString, tx.outputs(p.subpool_id.toInt)
                .value))
              logger.info("Updates made, now running to db")
              nextUpdates.foreach {
                u =>
                  logger.info(s"Updating subpool ${u._1} with holding id ${u._2._1} and value ${u._2._2}")
                  val q = Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag && p.subpool_id === u._1 && p.block === params.regenPlaceBlock.toLong ).map(p => (p.holdingId, p.holdingVal))
                  Thread.sleep(50)
                  db.run(q.update(u._2._1, u._2._2))
              }
              val stateUpdates =  poolPlace._2.map(p => p.subpool_id -> (tx.outputs(p.subpool_id.toInt).spendingTxId.isDefined))
              if(stateUpdates.exists(_._2)) {
                stateUpdates.foreach {
                  u =>
                    logger.info(s"Updating pool states for subpool ${u._1}")
                    if (u._2) {
                      logger.info("Setting pool state to success!")
                      db.run(Tables.PoolStatesTable.filter(s => s.subpool_id === u._1 && s.subpool === poolTag).map(s => s.status).update(PoolState.SUCCESS))
                    } else {
                      logger.info("Setting pool state to failure")
                      db.run(Tables.PoolStatesTable.filter(s => s.subpool_id === u._1 && s.subpool === poolTag).map(s => s.status).update(PoolState.FAILURE))
                    }

                }
              }
              if(stateUpdates.forall(_._2)){
                db.run(Tables.PoolBlocksTable
                  .filter(_.poolTag === poolTag)
                  .filter(_.gEpoch >= 376L)
                  .filter(_.gEpoch <=380L )
                  .map(b => b.status -> b.updated)
                  .update(PoolBlock.INITIATED -> LocalDateTime.now()))
              }
              logger.info(s"Completed placement regen for pool ${poolTag}")
            }else{
              logger.warn("Transaction was not defined!")
            }
        }
      }
    }


  }

  def regenStates = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    logger.info("Regening states")
    val poolTag = params.regenStatePool
    val outputBoxes = (expReq ? BoxesByTokenId(ErgoId.create(poolTag), 0, 100))
      .mapTo[Option[Seq[Output]]]
    logger.info("Now regening states")
    outputBoxes.onComplete{
      case Failure(exception) =>
        logger.error("There was an exceptiong getting outboxes", exception)
      case Success(value) =>
        logger.info("Values found!")
    }
    outputBoxes.map{
      optOutputs =>
        logger.info(s"${optOutputs.get.length} outputs found!")
        val outputs = optOutputs.get
        val unspent = outputs.filter(o => o.isOnMainChain && o.spendingTxId.isEmpty)
        logger.info(s"${unspent.length} unspent boxes")
        for(subpool <- unspent){
          logger.info(s"Subpool ${subpool.registers.R6.get.renderedValue.split(",")(3).toLong} attempting regen")
          logger.info(s"With tx ${subpool.txId} and box ${subpool.id}")
          db.run(Tables.PoolStatesTable.filter(o => o.subpool === poolTag).filter(o => o.subpool_id === subpool.registers.R6.get.renderedValue.split(",")(3).toLong)
          .map(s => (s.tx, s.box)).update(subpool.txId.toString -> subpool.id.toString))
        }
    }
  }
  def checkProcessingBlocks: Future[Unit] = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val queryBlocks = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PROCESSING).sortBy(_.created).result)
    val qPools = db.run(Tables.PoolInfoTable.result)
    for{
      blocks <- queryBlocks
      pools <- qPools
    } yield {
      val normalBlocks = PaymentRouter.routePlasmaBlocks(blocks, pools, routePlasma = false)
      val pooledBlocks = normalBlocks.groupBy(_.poolTag)
      for(poolBlock <- pooledBlocks){
        val poolToUse = pools.find(p => p.poolTag == poolBlock._1).get

        val blockBatch = {
          if(poolToUse.payment_type == PoolInformation.PAY_SOLO)
            poolBlock._2.sortBy(_.gEpoch).take(ConcurrentBoxLoader.BLOCK_BATCH_SIZE)
          else
            poolBlock._2.sortBy(_.gEpoch).take(ConcurrentBoxLoader.BLOCK_BATCH_SIZE)
        }
        val queryPlacements = (query ? PlacementsByBlock(blockBatch.head.poolTag, blockBatch.head.blockheight)).mapTo[Seq[PoolPlacement]]
        queryPlacements.map {
          placements =>

            placements.groupBy(_.holding_id).keys.foreach {
              holdingId =>
                verifyHoldingBoxes(blockBatch, holdingId)
            }
        }
      }
    }
  }
  def verifyHoldingBoxes(blocks: Seq[SPoolBlock], holdingId: String): Unit = {
    implicit val timeout: Timeout = Timeout(60 seconds)

    val boxFromExp = (expReq ? BoxesById(ErgoId.create(holdingId)))
    boxFromExp.onComplete {
      case Success(value) =>
        value match {
          case outputOpt: Option[Output] =>
            outputOpt match {
              case Some(output) =>
                if(output.isOnMainChain && output.spendingTxId.isEmpty) {
                  logger.info(s"Found unspent holding box ${holdingId}, now updating to processed")
                  db.run(Tables.PoolBlocksTable
                    .filter(b => b.poolTag === blocks.head.poolTag)
                    .filter(b => b.gEpoch >= blocks.head.gEpoch && b.gEpoch <= blocks.last.gEpoch)
                    .map(b => b.status -> b.updated)
                    .update(PoolBlock.PROCESSED, LocalDateTime.now())
                  )
                }else{
                  logger.warn(s"Holding box ${holdingId} was found, but was either on a forked chain or was already spent!")
                  logger.warn("Not deleting placements")
                  //write ! DeletePlacementsAtBlock(block.poolTag, block.blockheight)
                  if(!output.isOnMainChain) {

                    logger.warn("Holding box was not on the main chain!")
                    if (Instant.now().toEpochMilli - blocks.head.updated.toInstant(ZoneOffset.UTC).toEpochMilli > params.restartPlacements.toMillis){
                      logger.warn(s"It has been ${params.restartPlacements.toString()} since block was updated," +
                        s" now restarting placements for pool ${blocks.head.poolTag}")
                      //write ! UpdatePoolBlockStatus(PoolBlock.CONFIRMED, block.blockheight)
                    }

                  }else if(output.spendingTxId.isDefined){
                    logger.warn("Holding box was found to have an already spent transaction id! Setting block status to initiated!")
                   // write ! UpdatePoolBlockStatus(PoolBlock.INITIATED, block.blockheight)
                  }
                }
              case None =>
                if (Instant.now().toEpochMilli - blocks.head.updated.toInstant(ZoneOffset.UTC).toEpochMilli > params.restartPlacements.toMillis) {
                  logger.warn(s"It has been ${params.restartPlacements.toString()} since block was updated," +
                    s" now restarting placements for pool ${blocks.head.poolTag}")
                  // write ! DeletePlacementsAtBlock(blocks.head.poolTag, blocks.head.blockheight)
                  db.run(Tables.PoolBlocksTable
                    .filter(b => b.poolTag === blocks.head.poolTag)
                    .filter(b => b.gEpoch >= blocks.head.gEpoch && b.gEpoch <= blocks.last.gEpoch)
                    .map(b => b.status -> b.updated)
                    .update(PoolBlock.PRE_PROCESSED, LocalDateTime.now()))
                } else {
                  logger.warn("ExplorerReqBus returned no Output, but restartPlacements time has not passed for this block!")
                }
            }
            logger.info(s"Completed updates for processing block ${blocks.head.blockheight}")
          case TimeoutError(ex) =>
            logger.error("Received a socket timeout from ExplorerRequestBus, refusing to modify pool states!")
            throw ex
          case FatalExplorerError(ex) =>
            logger.error("Received a fatal error from ExplorerRequestBus, refusing to modify pool states!")
            throw ex
        }
      case Failure(exception) =>
        logger.error(s"There was a critical error grabbing holding box ${holdingId} from the explorer!", exception)
    }
  }

  def checkDistributions: Unit = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val queryBlocks = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.INITIATED).sortBy(_.created).result)
    val qPools = db.run(Tables.PoolInfoTable.result)
    for {
      blocks <- queryBlocks
      pools <- qPools
    }
    yield {
        val normalBlocks = PaymentRouter.routePlasmaBlocks(blocks, pools, routePlasma = false)
        val pooledBlocks = normalBlocks.groupBy(_.poolTag)
        for (poolBlock <- pooledBlocks) {
          val poolToUse = pools.find(p => p.poolTag == poolBlock._1).get
          val blocksToUse = {
            if(poolToUse.payment_type == PoolInformation.PAY_SOLO){
              poolBlock._2.take(ConcurrentBoxLoader.BLOCK_BATCH_SIZE)
            }else{
              poolBlock._2.take(ConcurrentBoxLoader.BLOCK_BATCH_SIZE)
            }
          }
          val queryPoolStates = (query ? QueryAllSubPools(poolBlock._1)).mapTo[Seq[PoolState]]
          validateDistStates(blocksToUse, queryPoolStates)
      }
    }
  }

  def modifySuccessState(state: PoolState, txOpt: Option[TransactionData]): PoolState = {
        txOpt match {
          case Some(txData) =>
            // TODO: MIGHT HAVE TO CHANGE THIS CONSTANT LATER
            val holdingInput = txData.inputs(2)
            val nextStored = txData.outputs.find(o => o.address == holdingInput.address)
            val nextId = nextStored.map(s => s.id.toString).getOrElse("none")
            val nextVal = nextStored.map(s => s.value).getOrElse(0L)
            val nextState = state.makeConfirmed(txData.outputs.head.id.toString, nextId, nextVal)
            nextState
          case None =>
            if (Instant.now().toEpochMilli - state.updated.toInstant(ZoneOffset.UTC).toEpochMilli > params.restartDists.toMillis) {
              logger.warn(s"It has been ${params.restartDists.toString()} since block was updated, now setting" +
                s" subpool ${state.subpool_id} in pool ${state.subpool} to status failed")
              state.makeFailure
            } else {
              state
            }
        }
  }

  def validateDistStates(blocks: Seq[SPoolBlock], queryPoolStates: Future[Seq[PoolState]]): Future[Unit] = {
    implicit val timeout: Timeout = Timeout(80 seconds)
    queryPoolStates.map {
      poolStates =>
        if(!poolStates.forall(s => s.status == PoolState.CONFIRMED)) {
          handleUnconfirmedStates(blocks, poolStates)
        }else{
          logger.info(s"All pools had status confirmed for pool ${poolStates.head.subpool}")
          logger.info("Now updating block and pool information")
          db.run(Tables.PoolBlocksTable
            .filter(_.poolTag === blocks.head.poolTag)
            .filter(_.gEpoch >= blocks.head.gEpoch)
            .filter(_.gEpoch <= blocks.last.gEpoch)
            .map(b => b.status -> b.updated)
            .update(PoolBlock.PAID -> LocalDateTime.now()))
          val fPoolMembers = (query ? PoolMembersByGEpoch(blocks.head.poolTag, blocks.head.gEpoch)).mapTo[Seq[PoolMember]]
          val fPoolInfo = (query ? QueryPoolInfo(blocks.head.poolTag)).mapTo[PoolInformation]
          val fPoolMiners = db.run(Tables.PoolSharesTable.queryMinerPools)
          logger.info("Block status update complete")
          for{
            members <- fPoolMembers
            info <- fPoolInfo
            settings <- fPoolMiners
          } yield {
            enterNewPaymentStats(blocks.head, info, members, settings.toMap)
          }

        }
    }
  }

  def enterNewPaymentStats(block: SPoolBlock, info: PoolInformation, members: Seq[PoolMember], settings: Map[String, Option[String]]): Try[Unit] = {
    // Build db changes
    // TODO: Fix payments for tokens
    val payments = members.filter(m => m.paid > 0).map{
      m =>
        Payment(block.poolTag, m.miner, info.currency, Helpers.convertFromWhole(info.currency, m.paid),
          m.tx, None, LocalDateTime.now(), block.blockheight, block.gEpoch)
    }

    val balances = members.filter(m => m.subpool == settings.get(m.miner).flatten.getOrElse(params.defaultPoolTag)).map{
      m =>
        m.miner -> Helpers.convertFromWhole(info.currency, m.stored)
    }

    val balanceChanges = members.filter(_.change > 0).map{
      m =>
        BalanceChange(block.poolTag, m.miner, info.currency, Helpers.convertFromWhole(info.currency, m.change),
          m.tx, None, LocalDateTime.now(), block.blockheight, block.gEpoch)
    }

    val balancesToUpdate = balances.map{
      b =>
        Tables.Balances.insertOrUpdate(Balance(AppParameters.mcPoolId, b._1, b._2, LocalDateTime.now(), LocalDateTime.now()))
    }
    // Execute inserts and updates
    Try {
      logger.info("Initiating database writes")
      val paymentInserts = Tables.Payments ++= payments
      val changeInserts = Tables.BalanceChanges ++= balanceChanges

      balancesToUpdate.map(db.run)
      logger.info("Sending writes...")
      val payR = db.run(paymentInserts)
      val changeR = db.run(changeInserts)

      payR.recoverWith {
        case ex: Exception =>
          logger.error("There was an exception inserting payments!", ex)
          Future(Failure(ex))
      }
      changeR.recoverWith {
        case ex: Exception =>
          logger.error("There was an exception inserting balance changes!", ex)
          Future(Failure(ex))
      }
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while updating payment info!", ex)
        Failure(ex)
    }
    logger.info("Payment insertions complete")
    Try {
      val shareDeletes = Tables.PoolSharesTable.filter(_.created < LocalDateTime.now().minusWeeks(params.keepSharesWindowInWeeks))
      val statsDeletes = Tables.MinerStats.filter(_.created < LocalDateTime.now().minusWeeks(params.keepMinerStatsWindowInWeeks))
      val shareArcInserts = Tables.SharesArchiveTable forceInsertQuery shareDeletes
      val statsArcInserts = Tables.MinerStatsArchiveTable forceInsertQuery statsDeletes
      val shareInserts = db.run(shareArcInserts)
      val statsInserts = db.run(statsArcInserts)
      for {
        shareI <- shareInserts
        statsI <- statsInserts
      } yield {
        db.run(shareDeletes.delete)
        db.run(statsDeletes.delete)
      }
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while pruning shares and minerstats tables!", ex)
        Failure(ex)
    }
    logger.info("Table pruning complete")
    Try {
      // Update pool info
      write ! UpdatePoolInfo(block.poolTag, block.gEpoch, block.blockheight, members.length, members.map(_.stored).sum,
        info.total_paid + members.map(_.paid).sum)

      write ! DeletePlacementsAtBlock(block.poolTag, block.blockheight)
      logger.info("Finished updating info and deleting placements")
    }.recoverWith{
      case ex: Exception =>
        logger.error("There was a fatal exception while updating info and deleting placements!", ex)
        Failure(ex)
    }
    Try(logger.info("Pool data updates complete"))
  }

  def handleUnconfirmedStates(blocks: Seq[SPoolBlock], poolStates: Seq[PoolState]): Unit = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    val modifiedPoolStates = {
      val statesToCheck = poolStates.filter(s => s.status == PoolState.SUCCESS)
      val nextStates = Future.sequence(statesToCheck.map {
        state =>
          val verifyTx = (expReq ? TxById(ErgoId.create(state.tx)))
          logger.info(s"Modifying state for pool ${blocks.head.poolTag} with block ${blocks.head.blockheight}")
          val newState = verifyTx.map {
            case txOpt: Option[TransactionData] =>
              modifySuccessState(state.copy(g_epoch = blocks.head.gEpoch), txOpt)
            case TimeoutError(ex) =>
              logger.error("Received a socket timeout from ExplorerRequestBus, refusing to modify pool states!")
              throw ex
            case FatalExplorerError(ex) =>
              logger.error("Received a fatal error from ExplorerRequestBus, refusing to modify pool states!")
              throw ex
          }
          newState
      })
      nextStates
    }

    modifiedPoolStates.onComplete {
      case Success(newStates) =>
        logger.warn(s"Updating pool ${newStates.head.subpool} with ${newStates.count(s => s.status == PoolState.FAILURE)}" +
          s" failures and ${newStates.count(s => s.status == PoolState.CONFIRMED)} confirmations")
        write ! UpdateWithNewStates(newStates.toArray)

        logger.warn("Now deleting members for the failed subpools")
        val newFailedStates = newStates.filter(s => s.status == PoolState.FAILURE)
        if(newFailedStates.nonEmpty) {
          newStates.filter(s => s.status == PoolState.FAILURE).foreach {
            s =>
              write ! DeleteSubPoolMembers(blocks.head.poolTag, blocks.head.gEpoch, s.subpool_id)
          }
        }

        if(newStates.exists(s => s.status == PoolState.FAILURE) || poolStates.exists(s => s.status == PoolState.FAILURE)){
          logger.info("Now setting block back to processed state due to existence of failures")
          db.run(Tables.PoolBlocksTable
            .filter(_.poolTag === blocks.head.poolTag)
            .filter(_.gEpoch >= blocks.head.gEpoch)
            .filter(_.gEpoch <= blocks.last.gEpoch)
            .map(b => b.status -> b.updated)
            .update(PoolBlock.PROCESSED -> LocalDateTime.now()))

        }
        logger.info("Pool state modifications complete")
      case Failure(exception) =>
        logger.error("There was a critical error while creating new pool states", exception)
    }
  }
}
