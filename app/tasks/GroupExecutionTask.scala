package tasks

import actors.BlockingDbWriter.{DeletePlacementsAtBlock, InsertMembers, InsertPlacements, UpdateBlockStatus, UpdatePoolBlockConf, UpdatePoolBlockStatus, UpdatePoolGEpoch, UpdateWithNewStates, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{BlockByHash, GetCurrentHeight, ValidateBlockByHeight}
import actors.GroupRequestHandler.{ConstructDistribution, ConstructHolding, DistributionComponents, DistributionResponse, ExecuteDistribution, ExecuteHolding, FailedPlacements, HoldingComponents, HoldingResponse}
import actors.QuickDbReader.{BlockByHeight, MinersByAssignedPool, PlacementsByBlock, PoolBlocksByStatus, QueryAllSubPools, QueryLastPlacement, QueryPoolInfo, QueryWithShareHandler, SettingsForMiner}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.explorer.Models.BlockContainer
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups.stages.roots.{DistributionRoot, EmissionRoot, HoldingRoot}
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.Models.{Block, MinerSettings, PoolBlock, PoolInformation, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BoxOperations, ErgoClient, ErgoId, InputBox, Parameters}
import org.ergoplatform.wallet.boxes.BoxSelector
import persistence.shares.ShareCollector
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile
import utils.{ConcurrentBoxLoader, DistributionFunctions, PlacementFunctions}
import utils.ConcurrentBoxLoader.BlockSelection

import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.{Inject, Named, Singleton}
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Singleton
class GroupExecutionTask @Inject()(system: ActorSystem, config: Configuration,
                                   @Named("quick-db-reader") query: ActorRef, @Named("blocking-db-writer") write: ActorRef,
                                   @Named("explorer-req-bus") expReq: ActorRef, @Named("group-handler") groupHandler: ActorRef,
                                   protected val dbConfigProvider: DatabaseConfigProvider)
                                   extends HasDatabaseConfigProvider[PostgresProfile]{
  import dbConfig.profile.api._
  val logger: Logger = Logger("GroupExecution")
  val taskConfig: TaskConfiguration = new TasksConfig(config).groupExecConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val ergoClient: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)


  if(taskConfig.enabled) {
    logger.info(s"GroupExecution Task will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>

      logger.info("GroupExecution has begun")
        val boxLoader: ConcurrentBoxLoader = new ConcurrentBoxLoader(query, ergoClient, params, contexts)

        val tryPreCollection = Try {
          boxLoader.preLoadInputBoxes(params.amountToPreCollect)
        }
        if(tryPreCollection.isSuccess) {
          val distributionFunctions = new DistributionFunctions(query, write, expReq, groupHandler, contexts, params, taskConfig, boxLoader)
          val placementFunctions = new PlacementFunctions(query, write, expReq, groupHandler, contexts, params, taskConfig, boxLoader, db)
          val tryPlacement = Try {
            placementFunctions.executePlacement()
          }
          val tryDist = Try {
            distributionFunctions.executeDistribution()
          }

          tryPlacement match {
            case Success(value) =>
              logger.info("Synchronous placement functions executed successfully!")
            case Failure(exception) =>
              logger.error("There was a fatal error thrown during synchronous placement execution", exception)
          }

          tryDist match {
            case Success(value) =>
              logger.info("Synchronous distribution functions executed successfully!")
            case Failure(exception) =>
              logger.error("There was a fatal error thrown during synchronous distribution execution", exception)
          }


        }else{
          logger.error("There was an error thrown while trying to pre-collect inputs!", tryPreCollection.failed.get)
        }

    })(contexts.taskContext)
  }else{
    logger.info("GroupExecution Task was not enabled")
  }
}
