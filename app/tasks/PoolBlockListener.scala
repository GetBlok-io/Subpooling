package tasks

import actors.BlockingDbWriter.{PostBlock, UpdateBlockStatus, UpdatePoolBlockConf, UpdatePoolBlockStatus, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{GetCurrentHeight, ValidateBlockByHeight}
import actors.QuickDbReader.{QueryPending, QueryPoolInfo, SettingsForMiner}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo, ValidBlock}
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{Block, MinerSettings, PoolBlock, PoolInformation}
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile

import javax.inject.{Inject, Named, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Singleton
class PoolBlockListener @Inject()(system: ActorSystem, config: Configuration,
                                  @Named("quick-db-reader") query: ActorRef, @Named("blocking-db-writer") write: ActorRef,
                                  @Named("explorer-req-bus") explorerReqBus: ActorRef,
                                  protected val dbConfigProvider: DatabaseConfigProvider)
                                  extends HasDatabaseConfigProvider[PostgresProfile]{

  import dbConfig.profile.api._
  val logger: Logger = Logger("PoolBlockListener")
  val taskConfig: TaskConfiguration = new TasksConfig(config).poolBlockConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)



  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)

  if(taskConfig.enabled) {
    logger.info(s"PoolBlockListener Task will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>
      logger.info("PoolBlockListener has begun execution, now initiating block evaluation")
        val tryEvaluate = Try
        {
          implicit val timeout: Timeout = Timeout(60 seconds)
          implicit val ec: ExecutionContext = contexts.taskContext
          val pendingBlocks = (query ? QueryPending(params.numToValidate)).mapTo[Seq[Block]]
          pendingBlocks.map{
            blocks =>
              blocks.foreach{
                b =>
                  val settingsQuery = db.run(Tables.MinerSettingsTable.filter(_.address === b.miner).result)

                  settingsQuery.map{
                    s =>
                      val postBlock = if(s.nonEmpty) {
                        logger.info(s"Now posting block ${b.blockheight} with miner ${b.miner} to pool ${s.head.subpool.getOrElse(params.defaultPoolTag)}")
                        (write ? PostBlock(b.blockheight, s.head.subpool.getOrElse(params.defaultPoolTag))).mapTo[Long].flatMap {
                          rows =>
                            (write ? UpdateBlockStatus(Block.TRANSFERRED, b.blockheight)).mapTo[Long]
                        }
                      }else{
                        logger.warn(s"No settings were found for block miner ${b.miner}, defaulting to normal pool")
                        logger.info(s"Now posting block ${b.blockheight} with miner ${b.miner} to default pool ${params.defaultPoolTag}")
                        (write ? PostBlock(b.blockheight, params.defaultPoolTag)).mapTo[Long].flatMap {
                          rows =>
                            (write ? UpdateBlockStatus(Block.TRANSFERRED, b.blockheight)).mapTo[Long]
                        }
                      }
                      postBlock.onComplete{
                        case Success(value) =>
                          write ! UpdatePoolBlockStatus(PoolBlock.VALIDATING, b.blockheight)
                        case Failure(exception) =>
                          logger.error(s"There was a critical error while posting block ${b.blockheight}" +
                            s" with miner ${b.miner}", exception)
                      }
                  }
              }
          }
        }
        tryEvaluate match {
          case Success(value) =>
            logger.debug("Block updates exited successfully")
          case Failure(exception) =>
            logger.error("There was an error thrown during block evaluation", exception)
        }

    })(contexts.taskContext)
  }else{
    logger.info("PoolBlockListener Task was not enabled")
  }
}
