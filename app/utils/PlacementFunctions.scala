package utils

import actors.BlockingDbWriter.{InsertPlacements, UpdatePoolBlockStatus}
import actors.GroupRequestHandler.{ConstructHolding, ExecuteHolding, HoldingComponents, HoldingResponse}
import actors.QuickDbReader._
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.Models._
import org.ergoplatform.appkit.{InputBox, Parameters}
import org.slf4j.{Logger, LoggerFactory}
import persistence.shares.{ShareCollector, ShareHandler}
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader.BlockSelection

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PlacementFunctions(query: ActorRef, write: ActorRef, expReq: ActorRef, groupHandler: ActorRef,
                         contexts: Contexts, params: ParamsConfig, taskConf: TaskConfiguration,
                         boxLoader: ConcurrentBoxLoader, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("PlacementFunctions")

  def executePlacement(): Unit = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val blockResp = query ? PoolBlocksByStatus(PoolBlock.CONFIRMED)
    // TODO: Change pending block num to group exec num
    logger.info(s"Querying blocks with confirmed status")
    val blocks = Await.result(blockResp.mapTo[Seq[PoolBlock]], 10 seconds).take(params.pendingBlockNum * 2)
    if(blocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(blocks, distinctOnly = !params.parallelPoolPlacements)
      val blockBoxMap = collectHoldingInputs(selectedBlocks)
      val holdingComponents = constructHoldingComponents(selectedBlocks)

      holdingComponents.onComplete {
        case Success(components) =>
          val executions = components.map {
            holdingComp =>
              val inputBoxes = blockBoxMap(holdingComp.block.blockheight)

              logger.info(s"Using input boxes with values: ${inputBoxes.map(b => b.getValue.toLong).mkString}")
              holdingComp.root match {
                case root: HoldingRoot =>
                  root.inputBoxes = Some(inputBoxes)
                case root: EmissionRoot =>
                  root.inputBoxes = Some(inputBoxes)
                case root: ExchangeEmissionsRoot =>
                  root.inputBoxes = Some(inputBoxes)
                case root: ProportionalEmissionsRoot =>
                  root.inputBoxes = Some(inputBoxes)
              }
              holdingComp.builder.inputBoxes = Some(inputBoxes)
              val holdResponse = (groupHandler ? ExecuteHolding(holdingComp)).mapTo[HoldingResponse]
              evalHoldingResponse(holdResponse)
          }

          val allComplete = Future.sequence(executions)
          allComplete.onComplete{
            case Success(value) =>
              logger.info("All placement executions completed!")
            case Failure(exception) =>
              logger.warn("Placement executions failed!")
          }
        case Failure(exception) =>
          logger.error("There was an error collecting holding components!", exception)
      }
    }else{
      logger.info("No confirmed blocks found for placements, now exiting placement execution.")
    }
  }

  def evalHoldingResponse(holdingResponse: Future[HoldingResponse]): Future[HoldingResponse] = {
    implicit val timeout: Timeout = Timeout(60 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    holdingResponse.onComplete{
      case Success(response) =>
        if(response.nextPlacements.nonEmpty) {
          logger.info(s"Holding execution was success for block ${response.block.blockheight} and pool ${response.nextPlacements.head}")
          logger.info("Now updating block to processing status and inserting placements into placements table")
          val blockUpdate = (write ? UpdatePoolBlockStatus(PoolBlock.PROCESSING, response.block.blockheight)).mapTo[Long]

          val placeInsertion = (write ? InsertPlacements(response.nextPlacements.head.subpool, response.nextPlacements)).mapTo[Long]
          val rowsUpdated = for{
            blockRowsUpdated <- blockUpdate
            placeRowsInserted <- placeInsertion
          } yield {
            if(blockRowsUpdated > 0)
              logger.info(s"${blockRowsUpdated} rows were updated for block ${response.block.blockheight}")
            else
              logger.error(s"No rows were updated for block ${response.block.blockheight}!")
            if(placeRowsInserted > 0)
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

  def constructHoldingComponents(blockSelections: Seq[BlockSelection]): Future[Seq[HoldingComponents]] = {
    implicit val timeout: Timeout = Timeout(120 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = blockSelections.map {
      blockSel =>
        val poolTag = blockSel.poolInformation.poolTag
        val block = blockSel.block

        val shareHandler = getShareHandler(blockSel.block, blockSel.poolInformation)
        val fCollector = Future(shareHandler.queryToWindow(block, params.defaultPoolTag))
        val poolStateResp = (query ? QueryAllSubPools(poolTag)).mapTo[Seq[PoolState]]
        val poolMinersResp = (query ? MinersByAssignedPool(poolTag)).mapTo[Seq[MinerSettings]]

        val holdingComponents = for{
          poolStates <- poolStateResp
          minerSettings <- poolMinersResp
          collector     <- fCollector
        } yield modifyHoldingData(poolStates, minerSettings, collector, blockSel)
        holdingComponents.flatten
    }
    Future.sequence(collectedComponents)
  }

  // TODO: Current parallelized implementation works well for ensuring multiple groups are executed,
  // TODO: But does not take into account that failure during box collection will cause a fatal error for all groups
  // TODO: In the future, ensure failure of one group does not affect others
  def collectHoldingInputs(blockSelections: Seq[BlockSelection]): Map[Long, Seq[InputBox]] = {
    var blockBoxMap = Map.empty[Long, Seq[InputBox]]
    // Make this a future
    for(blockSel <- blockSelections) {
      blockSel.poolInformation.currency match {
        case PoolInformation.CURR_ERG =>
          blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> boxLoader.collectFromLoaded(HoldingRoot.getMaxInputs(blockSel.block.getNanoErgReward)))
        case PoolInformation.CURR_TEST_TOKENS =>
          blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> boxLoader.collectFromLoaded(EmissionRoot.getMaxInputs(blockSel.block.getNanoErgReward)))
        case PoolInformation.CURR_NETA =>
          blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> boxLoader.collectFromLoaded(ExchangeEmissionsRoot.getMaxInputs(blockSel.block.getNanoErgReward)))
        case PoolInformation.CURR_ERG_COMET =>
          blockBoxMap = blockBoxMap + (blockSel.block.blockheight -> boxLoader.collectFromLoaded(ProportionalEmissionsRoot.getMaxInputs(blockSel.block.getNanoErgReward)))
      }

    }
    blockBoxMap
  }

  def modifyHoldingData(poolStates: Seq[PoolState], minerSettings: Seq[MinerSettings], collector: ShareCollector, blockSel: BlockSelection): Future[HoldingComponents] = {
    implicit val timeout: Timeout = Timeout(15 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext

    //collector.shareMap.retain((m, s) => minerSettings.exists(ms => ms.address == ms.))

    logger.info(s"Collector shareMap length: ${collector.shareMap.size}")
    logger.info(s"shareMap: ${collector.shareMap.toString()}")
    // TODO: Make pool flags (1) equal to share operator payment type

    // Collect new min payments for this placement
    val members = collector.toMembers.map{
      m =>
        // Double wrap option to account for null values
        val minPay = Option(minerSettings.find(s => s.address == m.address.toString).map(_.paymentthreshold).getOrElse(0.01))
        m.copy( memberInfo =
        m.memberInfo.withMinPay(
          (minPay.getOrElse(0.01) * BigDecimal(Parameters.OneErg)).longValue()
        )
      )
    }
    val poolTag = blockSel.poolInformation.poolTag
    val poolInformation = blockSel.poolInformation
    logger.info("Num members: " + members.length)
    val lastPlacementResp = (query ? PlacementsByGEpoch(blockSel.block.poolTag, blockSel.block.gEpoch-1)).mapTo[Seq[PoolPlacement]]
    lastPlacementResp.flatMap {
      placements =>
        if(placements.nonEmpty) {
          logger.info(s"Last placements at gEpoch ${blockSel.block.gEpoch - 1} were found for pool ${poolTag}")
          (groupHandler ? ConstructHolding(poolTag, poolStates,
            members, Some(placements), poolInformation, blockSel.block)).mapTo[HoldingComponents]
        }else {
          logger.warn(s"No last placement was found for pool ${poolTag} and block ${blockSel.block.blockheight} ")
          (groupHandler ? ConstructHolding(poolTag, poolStates,
            members, None, poolInformation, blockSel.block)).mapTo[HoldingComponents]
        }
    }
  }

  def getShareHandler(block: PoolBlock, information: PoolInformation): ShareHandler = {
    information.payment_type match {
      case PoolInformation.PAY_PPLNS =>
        new ShareHandler(PaymentType.PPLNS_WINDOW, block.miner, db)
      case PoolInformation.PAY_SOLO =>
        new ShareHandler(PaymentType.SOLO_SHARES, block.miner, db)
      case PoolInformation.PAY_EQ =>
        new ShareHandler(PaymentType.EQUAL_PAY, block.miner, db)
      case _ =>
        logger.warn(s"Could not find a payment type for pool ${information.poolTag}, defaulting to PPLNS Window")
        new ShareHandler(PaymentType.PPLNS_WINDOW, block.miner, db)
    }
  }
}
