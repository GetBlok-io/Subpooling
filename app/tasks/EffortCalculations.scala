package tasks

import actors.BlockingDbWriter.{UpdateBlockEffort, UpdatePoolBlockConf, UpdatePoolBlocksFound, UpdateWithValidation}
import actors.ExplorerRequestBus.ExplorerRequests.{GetCurrentHeight, ValidateBlockByHeight}
import actors.PushMessageNotifier.BlockMessage
import actors.QuickDbReader.{PoolBlocksByStatus, QueryBlocks, QueryPoolInfo}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig, TasksConfig}
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.{OrphanBlock, PartialBlockInfo, ValidBlock}
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolBlock, PoolInformation}
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile

import java.time.LocalDateTime
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

@Singleton
class EffortCalculations @Inject()(system: ActorSystem, config: Configuration,
                                   @Named("quick-db-reader") query: ActorRef, @Named("blocking-db-writer") write: ActorRef,
                                   @Named("explorer-req-bus") explorerReqBus: ActorRef, @Named("push-msg-notifier") push: ActorRef,
                                   protected val dbConfigProvider: DatabaseConfigProvider)
                                    extends HasDatabaseConfigProvider[PostgresProfile]{
  import dbConfig.profile.api._
  val logger: Logger = Logger("EffortCalculations")
  val taskConfig: TaskConfiguration = new TasksConfig(config).effortCalcConfig
  val nodeConfig: NodeConfig        = new NodeConfig(config)



  val contexts: Contexts = new Contexts(system)
  val params: ParamsConfig = new ParamsConfig(config)

  if(taskConfig.enabled) {
    logger.info(s"Effort Calculations will initiate in ${taskConfig.startup.toString()} with an interval of" +
      s" ${taskConfig.interval}")
    system.scheduler.scheduleWithFixedDelay(initialDelay = taskConfig.startup, delay = taskConfig.interval)({
      () =>
      logger.info("Now initiating effort calcs")
      evalNullEffortBlocks



    })(contexts.taskContext)
  }else{
    logger.info("Effort Calcs were not enabled")
  }


  def evalNullEffortBlocks = {
    logger.info("Now evaluating blocks with null effort")
    implicit val ec: ExecutionContext = contexts.taskContext
    implicit val timeout: Timeout = Timeout(70 seconds)
    val allBlocks = (query ? QueryBlocks(None)).mapTo[Seq[PoolBlock]]
    allBlocks.map{
      blocks =>
        updateBlockEffort(blocks)
    }
  }

  def updateBlockEffort(allBlocks: Seq[PoolBlock]) = {
    implicit val ec: ExecutionContext = contexts.taskContext
    implicit val timeout: Timeout = Timeout(700 seconds)
    val blocksGrouped = allBlocks.groupBy(_.poolTag)
    var blocksUpdated = 0
    logger.info("Evaluating effort for blocks!")
    blocksGrouped.foreach{
      bg =>
        val ordered = bg._2.filter(_.gEpoch != -1).sortBy(_.gEpoch)
        val orderedNoEffort = ordered.filter(_.effort.isEmpty)
        logger.info(s"Pool ${bg._1} has ${orderedNoEffort.length} blocks with null effort values!")
        orderedNoEffort.take(2).foreach{
          block =>
            if(block.gEpoch != 1){
              val lastBlock = ordered( ordered.indexWhere(b => b.poolTag == block.poolTag && b.blockheight == block.blockheight) - 1)
              val start = lastBlock.created
              val end = block.created
              logger.info(s"Querying miners for pool ${block.poolTag}")
              val fMiners = db.run(Tables.PoolSharesTable.queryMinerPools)
              val fInfo   = db.run(Tables.PoolInfoTable.filter(_.poolTag === block.poolTag).result.head)
              for{
                info <- fInfo
                miners <- fMiners
              }
              yield {

                logger.info(s"A total of ${miners.size} miners ")
                logger.info(s"Querying shares between last block ${lastBlock.blockheight} and current block ${block.blockheight}")
                val accumDiff = calculateEffort(start, end, block, miners.toMap, info)
                logger.info(s"Finished share query, now writing block effort for ${block.blockheight}")
                writeEffortForBlock(block, accumDiff)
              }
            }else{
              logger.info(s"gEpoch is 1 for block ${block.blockheight}, Not writing effort for block")
//              val date = block.created
//              val sharesBefore = db.run(Tables.PoolSharesTable.filter(_.created <= date).result)
//              logger.info(s"Finished share query, now writing block effort for ${block.blockheight}")
//              sharesBefore.map(s => writeEffortForBlock(block, s))
            }
        }
        blocksUpdated = blocksUpdated + orderedNoEffort.length
    }
    logger.info(s"Finished evaluating null effort blocks! A total $blocksUpdated blocks were updated")
  }


  def calculateEffort(startDate: LocalDateTime, endDate: LocalDateTime, block: PoolBlock, miners: Map[String, Option[String]], info: PoolInformation) = {
    var offset = 0
    var limit = 75000
    var accumDiff = BigDecimal(0)
    logger.info(s"Querying shares for effort between ${startDate} and ${endDate}")
    info.payment_type match {
      case PoolInformation.PAY_SOLO =>
        logger.info("Using SOLO effort calcs")

        while(offset != -1 && offset < 7500000){
          logger.info(s"Now querying ${limit} shares at offset ${offset} between dates")
          val shares = Await.result(db.run(Tables.PoolSharesTable.queryMinerSharesBetweenDate(startDate, endDate, block.miner, offset, limit)), 1000 seconds)
          accumDiff = accumDiff + ((shares.map(s => BigDecimal(s.difficulty)).sum) * AppParameters.shareConst)
          logger.info(s"Current accumulated difficulty: ${accumDiff}")
          if(shares.nonEmpty)
            offset = offset + limit
          else
            offset = -1
          if((accumDiff / block.netDiff) * 100 > 500){
            offset = -1
          }
        }
        logger.info(s"Finished querying shares. Final accumDiff: ${accumDiff}")
        accumDiff
      case PoolInformation.PAY_PLASMA_SOLO =>
        logger.info("Using SOLO effort calcs")

        while(offset != -1){
          logger.info(s"Now querying ${limit} shares at offset ${offset} between dates")
          val shares = Await.result(db.run(Tables.PoolSharesTable.queryMinerSharesBetweenDate(startDate, endDate, block.miner, offset, limit)), 1000 seconds)
          accumDiff = accumDiff + ((shares.map(s => BigDecimal(s.difficulty)).sum) * AppParameters.shareConst)
          logger.info(s"Current accumulated difficulty: ${accumDiff}")
          if(shares.nonEmpty)
            offset = offset + limit
          else
            offset = -1
          if((accumDiff / block.netDiff) * 100 > 300){
            offset = -1
          }
        }
        logger.info(s"Finished querying shares. Final accumDiff: ${accumDiff}")
        accumDiff
      case _ =>
        logger.info("Using PPLNS effort calcs")
        while(offset != -1){
          logger.info(s"Now querying ${limit} shares at offset ${offset} between dates")
          val shares = Await.result(db.run(Tables.PoolSharesTable.queryBetweenDate(startDate, endDate, offset, limit)), 1000 seconds)
            .filter{
              s =>
                miners.get(s.miner).flatten.getOrElse(params.defaultPoolTag) == block.poolTag
            }
          accumDiff = accumDiff + ((shares.map(s => BigDecimal(s.difficulty)).sum) * AppParameters.shareConst)
          logger.info(s"Current accumulated difficulty: ${accumDiff}")
          if(shares.nonEmpty)
            offset = offset + limit
          else
            offset = -1

          if((accumDiff / block.netDiff) * 100 > 500){
            offset = -1
          }
        }
        logger.info(s"Finished querying shares. Final accumDiff: ${accumDiff}")
        accumDiff
    }

  }


  def writeEffortForBlock(currentBlock: PoolBlock, accumDiff: BigDecimal): Unit = {
    logger.info(s"Writing effort for current block ${currentBlock.blockheight}")

    logger.info(s"Accumulated effort calculated: ${accumDiff}")
    val totalEffort = accumDiff / currentBlock.netDiff
    logger.info(s"Now updating block effort for block ${currentBlock} and poolTag ${currentBlock}")
    logger.info(s"Effort: ${(totalEffort * 100).toDouble}% ")
    write ! UpdateBlockEffort(currentBlock.poolTag, totalEffort.toDouble, currentBlock.blockheight)
  }
}
