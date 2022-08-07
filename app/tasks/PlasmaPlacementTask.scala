package tasks

import akka.actor.{ActorRef, ActorSystem}
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import org.ergoplatform.appkit.ErgoClient
import plasma_utils.{EmissionHandler, PaymentDistributor, PrePlacer}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile
import utils.{ConcurrentBoxLoader, DistributionFunctions, PlacementFunctions, PrePlacementFunctions}

import javax.inject.{Inject, Named, Singleton}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Singleton
class PlasmaPlacementTask @Inject()(system: ActorSystem, config: Configuration,
                                    @Named("quick-db-reader") query: ActorRef, @Named("em-handler") emHandler: ActorRef,
                                    protected val dbConfigProvider: DatabaseConfigProvider)
                                   extends HasDatabaseConfigProvider[PostgresProfile]{
  val logger: Logger = Logger("PlasmaPlacement")
  val taskConfig: TaskConfiguration = new TasksConfig(config).plasmaPlacement
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val ergoClient: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)


  if(taskConfig.enabled) {
    logger.info(s"PlasmaPlacement Task will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>

      logger.info("PlasmaPlacement has begun")
        val boxLoader: ConcurrentBoxLoader = new ConcurrentBoxLoader(query, ergoClient, params, contexts, wallet)
        val prePlacer = new PrePlacer(contexts, params, nodeConfig, boxLoader, db, emHandler)

        val tryPlacement = {
          Try{
            prePlacer.preparePlacements()
          }
        }

        tryPlacement match {
          case Success(value) =>
            logger.info("Synchronous PrePlacement functions executed successfully!")
          case Failure(exception) =>
            logger.error("There was a fatal error thrown during synchronous PrePlacement execution", exception)
        }
    })(contexts.taskContext)
  }else{
    logger.info("PlasmaPlacement Task was not enabled")
  }
}
