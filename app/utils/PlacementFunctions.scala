package utils

import actors.BlockingDbWriter.{InsertPlacements, UpdatePoolBlockStatus}
import actors.ExplorerRequestBus.ExplorerRequests.BoxesByTokenId
import actors.GroupRequestHandler.{ConstructHolding, ExecuteHolding, HoldingComponents, HoldingResponse}
import actors.QuickDbReader._
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.explorer.Models.Output
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups.entities.Member
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.Models._
import io.getblok.subpooling_core.registers.MemberInfo
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.ergoplatform.appkit.{Address, ErgoId, InputBox, Parameters}
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import persistence.shares.{ShareCollector, ShareHandler}
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader.{BatchSelection, BlockSelection}

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PlacementFunctions(query: ActorRef, write: ActorRef, expReq: ActorRef, groupHandler: ActorRef,
                         contexts: Contexts, params: ParamsConfig, taskConf: TaskConfiguration,
                         boxLoader: ConcurrentBoxLoader, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("PlacementFunctions")

  import slick.jdbc.PostgresProfile.api._

  def executePlacement(): Unit = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext

    val blockResp = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PRE_PROCESSED).sortBy(_.created).result)
    // TODO: Change pending block num to group exec num
    logger.info(s"Querying blocks with confirmed status")
    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds)
    if (blocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(blocks, distinctOnly = true)
      val blockBoxMap = collectHoldingInputs(selectedBlocks)
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
          allComplete.onComplete {
            case Success(value) =>
              logger.info("All placement executions completed!")
            case Failure(exception) =>
              logger.warn("Placement executions failed!", exception)
          }
        case Failure(exception) =>
          logger.error("There was an error collecting holding components!", exception)
      }
    } else {
      logger.info("No confirmed blocks found for placements, now exiting placement execution.")
    }
  }

  def evalHoldingResponse(holdingResponse: Future[HoldingResponse]): Future[HoldingResponse] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    holdingResponse.onComplete {
      case Success(response) =>
        if (response.nextPlacements.nonEmpty) {
          logger.info(s"Holding execution was success for batch starting with ${response.batchSelection.blocks.head.blockheight} and pool ${response.nextPlacements.head}")
          logger.info("Now updating batched blocks to processing status and inserting placements into placements table")
          val blockUpdate = (db.run(Tables.PoolBlocksTable
            .filter(_.poolTag === response.batchSelection.blocks.head.poolTag)
            .filter(_.gEpoch >= response.batchSelection.blocks.head.gEpoch)
            .filter(_.gEpoch <= response.batchSelection.blocks.last.gEpoch)
            .map(b => b.status -> b.updated)
            .update(PoolBlock.PROCESSING -> LocalDateTime.now())))

          val placeDeletes = {
            db.run(Tables.PoolPlacementsTable
              .filter(p => p.subpool === response.batchSelection.blocks.head.poolTag)
              .filter(p => p.block === response.batchSelection.blocks.minBy(b => b.gEpoch).blockheight)
              .delete)
          }
          val placeUpdates = placeDeletes.map {
            i =>
              logger.info(s"A total of ${i} rows were deleted from last placements")
              logger.info("Now inserting new placement rows!")
              db.run(Tables.PoolPlacementsTable ++= response.nextPlacements)
          }.flatten

          val rowsUpdated = for {
            blockRowsUpdated <- blockUpdate
            placeRowsInserted <- placeUpdates
          } yield {
            if (blockRowsUpdated > 0)
              logger.info(s"${blockRowsUpdated} rows were updated for ${response.batchSelection.blocks.length} blocks")
            else
              logger.error(s"No rows were updated for ${response.batchSelection.blocks.length} blocks!")
            if (placeRowsInserted.getOrElse(0) > 0)
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

      val currentPlacements = (db.run(Tables.PoolPlacementsTable.filter(_.block === block.blockheight).result)).mapTo[Seq[PoolPlacement]]

      val poolStateResp = (db.run(Tables.PoolStatesTable.filter(_.subpool === poolTag).result)).mapTo[Seq[PoolState]]
      val poolMinersResp = (db.run(Tables.PoolSharesTable.queryPoolMiners(poolTag, params.defaultPoolTag))).mapTo[Seq[SMinerSettings]]

      val holdingComponents = for {
        poolStates <- poolStateResp
        minerSettings <- poolMinersResp
        placements <- currentPlacements
      } yield modifyHoldingData(poolStates, minerSettings, placements, batchSelection)
      holdingComponents.flatten
    }
    collectedComponents
  }

  // TODO: Current parallelized implementation works well for ensuring multiple groups are executed,
  // TODO: But does not take into account that failure during box collection will cause a fatal error for all groups
  // TODO: In the future, ensure failure of one group does not affect others
  def collectHoldingInputs(batch: BatchSelection): Map[Long, Seq[InputBox]] = {
    var blockBoxMap = Map.empty[Long, Seq[InputBox]]
    // Make this a future
    val batchSum = Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum)
    batch.info.currency match {
      case PoolInformation.CURR_ERG =>
        blockBoxMap = blockBoxMap + (batch.blocks.head.blockheight -> boxLoader.collectFromLoaded(HoldingRoot.getMaxInputs(batchSum)))
      case PoolInformation.CURR_TEST_TOKENS =>
        blockBoxMap = blockBoxMap + (batch.blocks.head.blockheight -> boxLoader.collectFromLoaded(EmissionRoot.getMaxInputs(batchSum)))
      case PoolInformation.CURR_NETA =>
        blockBoxMap = blockBoxMap + (batch.blocks.head.blockheight -> boxLoader.collectFromLoaded(ExchangeEmissionsRoot.getMaxInputs(batchSum)))
      case PoolInformation.CURR_ERG_COMET =>
        blockBoxMap = blockBoxMap + (batch.blocks.head.blockheight -> boxLoader.collectFromLoaded(ProportionalEmissionsRoot.getMaxInputs(batchSum)))
    }


    blockBoxMap
  }

  def modifyHoldingData(poolStates: Seq[PoolState], minerSettings: Seq[SMinerSettings], placements: Seq[PoolPlacement], batch: BatchSelection): Future[HoldingComponents] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val poolTag = batch.info.poolTag
    val poolInformation = batch.info
    //collector.shareMap.retain((m, s) => minerSettings.exists(ms => ms.address == ms.))

    // Collect new min payments for this placement
    val members = placements.map(p => Member(Address.create(p.miner), new MemberInfo(Array(p.score, 0L, 0L, 0L, 0L)))).map{
      m =>
        // Double wrap option to account for null values
        val minPay = Option(minerSettings.find(s => s.address == m.address.toString).map(_.paymentthreshold).getOrElse(0.01))
        if(batch.info.payment_type != PoolInformation.PAY_SOLO){
          if(poolTag == "30afb371a30d30f3d1180fbaf51440b9fa259b5d3b65fe2ddc988ab1e2a408e7") {
            m.copy(memberInfo =
              m.memberInfo.withMinPay(
                (minPay.getOrElse(0.001) * BigDecimal(Helpers.OneErg)).longValue()
              ))
          }else{
            m.copy(memberInfo =
              m.memberInfo.withMinPay(
                (minPay.getOrElse(0.01) * BigDecimal(Helpers.OneErg)).longValue()
              ))
          }
        }else{
          m.copy(
            memberInfo = m.memberInfo.withMinPay((0.001 * BigDecimal(Helpers.OneErg)).longValue())
          )
        }
    }.toArray


    logger.info("Num members: " + members.length)

    // TODO
    val lastPlacementResp = db.run(Tables.PoolPlacementsTable.filter(p => p.gEpoch === batch.blocks.head.gEpoch - 5 && p.subpool === poolTag).result)
      .mapTo[Seq[PoolPlacement]]
    lastPlacementResp.flatMap {
      placements =>
        if (placements.nonEmpty) {
          logger.info(s"Last placements at gEpoch ${batch.blocks.head.gEpoch - 5} were found for pool ${poolTag}")
          (groupHandler ? ConstructHolding(poolTag, poolStates,
            members, Some(placements), poolInformation, batch, Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum), AppParameters.sendTxs)).mapTo[HoldingComponents]
        } else {
          logger.warn(s"No last placement was found for pool ${poolTag} and block ${batch.blocks.head} ")
          (groupHandler ? ConstructHolding(poolTag, poolStates,
            members, None, poolInformation, batch, Helpers.ergToNanoErg(batch.blocks.map(_.reward).sum), AppParameters.sendTxs)).mapTo[HoldingComponents]
        }
    }
  }
}
