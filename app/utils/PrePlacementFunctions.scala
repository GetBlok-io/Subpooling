package utils

import actors.ExplorerRequestBus.ExplorerRequests.BoxesByTokenId
import actors.GroupRequestHandler.{ConstructHolding, ExecuteHolding, HoldingComponents, HoldingResponse}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, NodeConfig, ParamsConfig}
import io.getblok.subpooling_core.explorer.Models.Output
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.PersistenceModels._
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.ergoplatform.appkit.{ErgoClient, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import persistence.shares.{ShareCollector, ShareHandler}
import plasma_utils.payments.PaymentRouter
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader.BatchSelection

import java.time.LocalDateTime
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PrePlacementFunctions(query: ActorRef, write: ActorRef, expReq: ActorRef, groupHandler: ActorRef,
                            contexts: Contexts, params: ParamsConfig, taskConf: TaskConfiguration,
                            nodeConfig: NodeConfig, boxLoader: ConcurrentBoxLoader, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("PrePlacementFunctions")
  import slick.jdbc.PostgresProfile.api._

  def executePrePlacement(): Unit = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext

    val blockResp = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.CONFIRMED).sortBy(_.created).result)
    val infoResp = db.run(Tables.PoolInfoTable.result)


    // TODO: Change pending block num to group exec num
    logger.info(s"Querying blocks with confirmed status")
    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds)
    val infos = Await.result(infoResp, 1000 seconds)

    val normalBlocks = PaymentRouter.routePlasmaBlocks(blocks, infos, routePlasma = false)
    if(normalBlocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(normalBlocks, strictBatch = true)
      val blockBoxMap = createFakeBoxMap(selectedBlocks)
      val holdingComponents = constructHoldingComponents(selectedBlocks)

      holdingComponents.onComplete {
        case Success(components) =>
          val executions = {
            val inputBoxes = blockBoxMap(components.batchSelection.blocks.head.blockheight)

            logger.info(s"Using input boxes with values: ${inputBoxes.map(b => b.getValue.toLong).mkString}")
            components.root match {
              case root: HoldingRoot =>
                root.inputBoxes = Some(inputBoxes)
              case root: EmissionRoot =>
                root.inputBoxes = Some(inputBoxes)
              case root: ExchangeEmissionsRoot =>
                root.inputBoxes = Some(inputBoxes)
                // TODO: Find generalized solution
                val lpBoxes = Await.result((expReq ? BoxesByTokenId(EmissionTemplates.NETA_MAINNET.lpNFT, 0, 100))
                  .mapTo[Option[Seq[Output]]], 1000 seconds)

                logger.info("Finished getting lpBoxes!")
                val boxToUse = lpBoxes.get.filter(l => l.isOnMainChain && l.spendingTxId.isEmpty).head
                logger.info(s"Using lpBox with id ${boxToUse.id}")
                root.lpBoxId = Some(boxToUse.id)
              case root: ProportionalEmissionsRoot =>
                root.inputBoxes = Some(inputBoxes)
            }
            components.builder.inputBoxes = Some(inputBoxes)
            val holdResponse = (groupHandler ? ExecuteHolding(components)).mapTo[HoldingResponse]
            evalHoldingResponse(holdResponse)
        }

          val allComplete = executions
          allComplete.onComplete{
            case Success(value) =>
              logger.info("All placement executions completed!")
            case Failure(exception) =>
              logger.warn("Placement executions failed!", exception)
          }
        case Failure(exception) =>
          logger.error("There was an error collecting holding components!", exception)
      }
    }else{
      logger.info("No confirmed blocks found for placements, now exiting placement execution.")
    }
  }

  def evalHoldingResponse(holdingResponse: Future[HoldingResponse]): Future[HoldingResponse] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    holdingResponse.onComplete{
      case Success(response) =>
        if(response.nextPlacements.nonEmpty) {
          logger.info(s"Holding execution was success for batch starting with ${response.batchSelection.blocks.head.blockheight} and pool ${response.nextPlacements.head}")
          logger.info("Now updating batched blocks to processing status and inserting placements into placements table")
          val blockUpdate = (db.run(Tables.PoolBlocksTable
            .filter(_.poolTag === response.batchSelection.blocks.head.poolTag)
            .filter(_.gEpoch >= response.batchSelection.blocks.head.gEpoch)
            .filter(_.gEpoch <= response.batchSelection.blocks.last.gEpoch)
            .map(b => b.status -> b.updated)
            .update(PoolBlock.PRE_PROCESSED -> LocalDateTime.now())))

          val placeInsertion = db.run(Tables.PoolPlacementsTable ++= response.nextPlacements.toSeq)
          val rowsUpdated = for{
            blockRowsUpdated <- blockUpdate
            placeRowsInserted <- placeInsertion
          } yield {
            if(blockRowsUpdated > 0)
              logger.info(s"${blockRowsUpdated} rows were updated for ${response.batchSelection.blocks.length} blocks")
            else
              logger.error(s"No rows were updated for ${response.batchSelection.blocks.length} blocks!")
            if(placeRowsInserted.getOrElse(0) > 0)
              logger.info(s"${placeRowsInserted} rows were inserted for placements for pool ${response.nextPlacements.head.subpool}")
            else
              logger.error(s"No placements were inserted for pool ${response.nextPlacements.head.subpool}")
          }

        }
      case Failure(exception) =>
        logger.error("A fatal error occurred during evaluation of a holding response!", exception)
    }
    holdingResponse
  }

  def constructHoldingComponents(batchSelection: BatchSelection): Future[HoldingComponents] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = {
      val poolTag = batchSelection.info.poolTag
      val block = batchSelection.blocks.head
      logger.info(s"Now querying shares, pool states, and miner settings for block ${block.blockheight}" +
        s" and pool ${block.poolTag}")

      val fCollectors = Future.sequence{

        if(batchSelection.info.payment_type != PoolInformation.PAY_SOLO){
          val collectors = for(shareBlock <- batchSelection.blocks) yield {
            val shareHandler = getShareHandler(shareBlock, batchSelection.info)
            Future(shareHandler.queryToWindow(shareBlock, params.defaultPoolTag))
          }
          collectors
        }
        else {
          logger.info(s"Performing SOLO query for block ${block.blockheight} with poolTag ${block.poolTag}" +
            s" and miner ${block.miner}")

          val collectors = (batchSelection.blocks.map{
            b =>
              val shareHandler = getShareHandler(block, batchSelection.info)
              Future(shareHandler.addForSOLO(b))
          })
          collectors
        }
      }

      val fCollector = {
        if(batchSelection.info.payment_type != PoolInformation.PAY_SOLO) {
          fCollectors.map {
            collectors =>
              val merged = collectors.slice(1, collectors.length).foldLeft(collectors.head) {
                (head: ShareCollector, other: ShareCollector) =>
                  head.merge(other)
              }
              merged.avg(collectors.length)
          }
        }else{
          fCollectors.map {
            collectors =>
              val merged = collectors.slice(1, collectors.length).foldLeft(collectors.head) {
                (head: ShareCollector, other: ShareCollector) =>
                  head.merge(other)
              }
              merged
          }
        }
      }

      val poolStateResp = (db.run(Tables.PoolStatesTable.filter(_.subpool === poolTag).result)).mapTo[Seq[PoolState]]
      val poolMinersResp = (db.run(Tables.PoolSharesTable.queryPoolMiners(poolTag, params.defaultPoolTag))).mapTo[Seq[SMinerSettings]]

      val holdingComponents = for{
        poolStates <- poolStateResp
        minerSettings <- poolMinersResp
        collector     <- fCollector
      } yield modifyHoldingData(poolStates, minerSettings, collector, batchSelection)
      holdingComponents.flatten
    }
    collectedComponents
  }

  def createFakeBoxMap(batch: BatchSelection): Map[Long, Seq[InputBox]] = {
    var blockBoxMap = Map.empty[Long, Seq[InputBox]]
    val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"

    // Make this a future
    val batchSum = Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum)
    val totalAmount = batch.info.currency match {
      case PoolInformation.CURR_ERG =>
        HoldingRoot.getMaxInputs(batchSum)
      case PoolInformation.CURR_TEST_TOKENS =>
        EmissionRoot.getMaxInputs(batchSum)
      case PoolInformation.CURR_NETA =>
        ExchangeEmissionsRoot.getMaxInputs(batchSum)
      case PoolInformation.CURR_ERG_COMET =>
        ProportionalEmissionsRoot.getMaxInputs(batchSum)
    }

    val fakeBox = {
      nodeConfig.getClient.execute{
        ctx =>
          ctx.newTxBuilder().outBoxBuilder()
            .value(totalAmount)
            .contract(nodeConfig.getNodeWallet.contract)
            .build()
            .convertToInputWith(dummyTxId, 0)
      }
    }

    blockBoxMap = Map(batch.blocks.head.blockheight -> Seq(fakeBox))
    blockBoxMap
  }

  def modifyHoldingData(poolStates: Seq[PoolState], minerSettings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection): Future[HoldingComponents] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext

    //collector.shareMap.retain((m, s) => minerSettings.exists(ms => ms.address == ms.))

    logger.info(s"Collector shareMap length: ${collector}")
    // TODO: Make pool flags (1) equal to share operator payment type

    // Collect new min payments for this placement
    val members = collector.toMembers.map{
      m =>
        // Double wrap option to account for null values
        val minPay = Option(minerSettings.find(s => s.address == m.address.toString).map(_.paymentthreshold).getOrElse(0.01))
        if((batch.info.payment_type != PoolInformation.PAY_SOLO) || (batch.info.poolTag == "4342b4a582c18a0e77218f1aa2de464ae1b46ad66c30abc6328e349e624e9047")){
          m.copy( memberInfo =
          m.memberInfo.withMinPay(
            (minPay.getOrElse(0.001) * BigDecimal(Helpers.OneErg)).longValue()
          ))
        }else{
          m.copy(
            memberInfo = m.memberInfo.withMinPay((0.001 * BigDecimal(Helpers.OneErg)).longValue())
          )
        }
    }
    val poolTag = batch.info.poolTag
    val poolInformation = batch.info

    logger.info("Num members: " + members.length)

    // TODO
    val lastPlacementResp = db.run(Tables.PoolPlacementsTable.filter(p => p.gEpoch === batch.blocks.head.gEpoch - 5 && p.subpool === poolTag).result)
      .mapTo[Seq[PoolPlacement]]
    lastPlacementResp.flatMap {
      placements =>
        if(placements.nonEmpty) {
          logger.info(s"Last placements at gEpoch ${batch.blocks.head.gEpoch - 5} were found for pool ${poolTag}")
          (groupHandler ? ConstructHolding(poolTag, poolStates,
            members, Some(placements), poolInformation, batch, Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum), sendTxs = false)).mapTo[HoldingComponents]
        }else {
          logger.warn(s"No last placement was found for pool ${poolTag} and block ${batch.blocks.head} ")
          (groupHandler ? ConstructHolding(poolTag, poolStates,
            members, None, poolInformation, batch, Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum), sendTxs = false)).mapTo[HoldingComponents]
        }
    }
  }

  def getShareHandler(block: SPoolBlock, information: PoolInformation): ShareHandler = {
    information.payment_type match {
      case PoolInformation.PAY_PPLNS =>
        new ShareHandler(PaymentType.PPLNS_WINDOW, block.miner, db)
      case PoolInformation.PAY_SOLO =>
        new ShareHandler(PaymentType.SOLO_BATCH, block.miner, db) // TODO: Use solo batch payments
      case PoolInformation.PAY_EQ =>
        new ShareHandler(PaymentType.EQUAL_PAY, block.miner, db)
      case _ =>
        logger.warn(s"Could not find a payment type for pool ${information.poolTag}, defaulting to PPLNS Window")
        new ShareHandler(PaymentType.PPLNS_WINDOW, block.miner, db)
    }
  }
}
