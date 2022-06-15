package tasks

import actors.BlockingDbWriter._
import actors.ExplorerRequestBus.ExplorerRequests.{BoxesById, FatalExplorerError, TimeoutError, TxById}
import actors.QuickDbReader._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.explorer.Models.{Output, TransactionData}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.Models._
import models.DatabaseModels.{Balance, BalanceChange, Payment, SMinerSettings}
import org.ergoplatform.appkit.{ErgoClient, ErgoId}
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile

import java.time.{Instant, LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{higherKinds, postfixOps}
import scala.util.{Failure, Success, Try}

@Singleton
class StatsRecorder @Inject()(system: ActorSystem, config: Configuration,
                              @Named("explorer-req-bus") expReq: ActorRef,
                              protected val dbConfigProvider: DatabaseConfigProvider)
                                    extends HasDatabaseConfigProvider[PostgresProfile]{

  import dbConfig.profile.api._
  val logger: Logger = Logger("StatsRecorder")
  // TODO: Change task config
  val taskConfig: TaskConfiguration = new TasksConfig(config).dbCrossCheckConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val ergoClient: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)
  var blockQueue: ArrayBuffer[Block] = ArrayBuffer.empty[Block]

  implicit val ec: ExecutionContext = contexts.taskContext
  if(taskConfig.enabled) {
    logger.info(db.source.toString)
    logger.info(dbConfig.profileName)
    logger.info(s"DatabaseCrossCheck will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>

    })(contexts.taskContext)
  }

  def recordNewMiners = {
    // TODO: Add minerstats limit to config
    val currentMiners = db.run(Tables.MinerStats.sortBy(_.created.desc).distinctOn(_.miner).take(1000).result)
    val recordedMiners = db.run(Tables.MinerSettingsTable.result)
    logger.info(s"Querying miners in stats and settings")
    for{
      currMiners <- currentMiners
      minerSettings <- recordedMiners
    } yield {
      val statsSet = Set(currMiners.map(_.miner):_*)
      val settingsSet = Set(minerSettings.map(_.address):_*)
      logger.info(s"Miners in stats: ${statsSet.size}")
      logger.info(s"Miners in settings: ${settingsSet}")
      val diff = statsSet -- settingsSet
      val newSettings = for(addr <- diff) yield SMinerSettings(AppParameters.mcPoolId, addr, 0.01, LocalDateTime.now(), LocalDateTime.now(), Some(params.defaultPoolTag))
      db.run(Tables.MinerSettingsTable ++= newSettings)
    }
  }



}
