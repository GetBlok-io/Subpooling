package tasks

import actors.ExplorerRequestBus.ExplorerRequests.GetCurrentHeight
import actors.QuickDbReader.{PlacementsByBlock, PoolBlocksByStatus}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import configs.TasksConfig.TaskConfiguration
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolBlock}
import org.ergoplatform.appkit.ErgoClient
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

@Singleton
class DbCrossCheck @Inject()(system: ActorSystem, config: Configuration,
                                   @Named("quick-db-reader") query: ActorRef, @Named("blocking-db-writer") write: ActorRef,
                                   @Named("explorer-req-bus") expReq: ActorRef) {
  val logger: Logger = Logger("DatabaseCrossCheck")
  val taskConfig: TaskConfiguration = new TasksConfig(config).dbCrossCheckConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)
  val ergoClient: ErgoClient = nodeConfig.getClient
  val wallet:     NodeWallet = nodeConfig.getNodeWallet

  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)
  var blockQueue: ArrayBuffer[Block] = ArrayBuffer.empty[Block]
  implicit val ec: ExecutionContext = contexts.taskContext
  if(taskConfig.enabled) {
    logger.info(s"DatabaseCrossCheck will initiate in ${taskConfig.startup.toString()} with an interval of " +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleAtFixedRate(initialDelay = taskConfig.startup, interval = taskConfig.interval)({
      () =>
    })(contexts.taskContext)
  }

  def checkProcessingBlocks = {
    implicit val timeout: Timeout = Timeout(15 seconds)
    val queryBlocks = (query ? PoolBlocksByStatus(PoolBlock.PROCESSING)).mapTo[Seq[PoolBlock]]
    val futHeight   = (expReq ? GetCurrentHeight).mapTo[Int]
    for{
      blocks <- queryBlocks
      height <- futHeight
    } yield {

    }
  }

  def processBlock(block: PoolBlock) = {
    implicit val timeout: Timeout = Timeout(15 seconds)
    val queryPlacements = (query ? PlacementsByBlock(block.poolTag ,block.blockheight))
  }

  def checkDistributions = ???
}
