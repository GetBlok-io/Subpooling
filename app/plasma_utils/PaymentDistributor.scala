package plasma_utils

import actors.BlockingDbWriter._
import actors.ExplorerRequestBus.ExplorerRequests.GetCurrentHeight
import actors.GroupRequestHandler._
import actors.StateRequestHandler.{ConstructedDist, DistConstructor, DistResponse, ExecuteDist, StateFailure}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups.stages.roots.DistributionRoot
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolBlock, PoolInformation, PoolPlacement, PoolState}
import io.getblok.subpooling_core.plasma.StateConversions.balanceConversion
import io.getblok.subpooling_core.plasma.{BalanceState, SingleBalance, StateBalance}
import io.getblok.subpooling_core.states.groups.StateGroup
import io.getblok.subpooling_core.states.models.TransformResult
import models.DatabaseModels.SPoolBlock
import org.ergoplatform.appkit.InputBox
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import plasma_utils.payments.PaymentRouter
import plasma_utils.stats.StatsRecorder
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader
import utils.ConcurrentBoxLoader.{BLOCK_BATCH_SIZE, BatchSelection, PLASMA_BATCH_SIZE}

import java.time.LocalDateTime
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PaymentDistributor(expReq: ActorRef, stateHandler: ActorRef,
                         contexts: Contexts, params: ParamsConfig, taskConf: TaskConfiguration,
                         boxLoader: ConcurrentBoxLoader, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("PaymentDistributor")
  import slick.jdbc.PostgresProfile.api._

  def executeDistribution(): Unit = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    logger.info("Now querying processed blocks for distribution")
    val blockResp = db.run(Tables.PoolBlocksTable.filter(_.status === PoolBlock.PROCESSED).sortBy(_.created).result)
    val infoResp = db.run(Tables.PoolInfoTable.result)
    logger.info(s"Querying blocks with processed status")
    val blocks = Await.result(blockResp.mapTo[Seq[SPoolBlock]], 1000 seconds)
    val infos = Await.result(infoResp, 1000 seconds)

    val plasmaBlocks = PaymentRouter.routePlasmaBlocks(blocks, infos, routePlasma = true)
    if(plasmaBlocks.nonEmpty) {
      val selectedBlocks = boxLoader.selectBlocks(plasmaBlocks, strictBatch = true, isPlasma = true)
      val inputBoxes = collectInputs(selectedBlocks)
      val collectedComponents = constructStateGroup(selectedBlocks, inputBoxes)

      collectedComponents.onComplete {
        case Success(constDist) =>
          val executions = {

            logger.info("Now sending dist req")
            val distResponse = (stateHandler ? ExecuteDist(constDist, AppParameters.sendTxs)).mapTo[DistResponse[StateBalance]]
            logger.info("Waiting to eval dist response")
            writeDist(distResponse)
          }

        case Failure(exception) =>
          logger.error("There was an error collecting distribution components!", exception)
      }
    }else{
      logger.info("No processing blocks found, now exiting distribution execution")
    }
  }

  def writeDist(distResponse: Future[DistResponse[StateBalance]]): Future[DistResponse[StateBalance]] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    distResponse.onComplete {
      case Success(dr) =>
        logger.info("Now evaluating dist response")
        if (dr.members.isEmpty) {
          logger.error("There was a fatal error during distribution execution, response returned empty")
          logger.error("No updates being made")
        } else {
          val transforms = dr.transforms.filter(_.isSuccess).map(_.get)
          val lastTf = transforms.last
          val members = dr.members
          val poolTag = members.head.subpool
          val gEpoch = members.head.g_epoch
          val nextStatus = {
            if(dr.transforms.exists(_.isFailure))
              PoolState.FAILURE
            else
              PoolState.SUCCESS
          }
          val nextState = dr.nextState.copy(tx = lastTf.id, epoch = gEpoch, height = lastTf.nextState.box.getCreationHeight,
            block = members.head.block, status = nextStatus, updated = LocalDateTime.now())

          val stateUpdates =
              db.run(Tables.PoolStatesTable.filter(s => s.subpool === poolTag).map{
                s => (s.tx, s.epoch, s.height, s.status, s.members, s.block, s.updated)
              }.update(nextState.tx, nextState.epoch, nextState.height, nextState.status, members.size, nextState.block, LocalDateTime.now()))


          stateUpdates.onComplete {
            case Success(rows) =>
              if (rows > 0) {
                logger.info(s"${rows} were updated successfully!")
              } else {
                logger.error("0 rows were updated!")
              }
            case Failure(exception) =>
              logger.error(s"There was an error updating the pool states for pool ${poolTag}", exception)
          }
          logger.info("Now incrementing gEpoch for all states")
          db.run(Tables.PoolStatesTable.filter(s => s.subpool === poolTag).map(_.gEpoch).update(gEpoch))

          val insertMembersReq = db.run(Tables.SubPoolMembers ++= dr.members)
          insertMembersReq.onComplete {
            case Success(rows) =>
              if (rows.getOrElse(0) > 0) {
                logger.info(s"$rows in the members table were updated successfully!")
              } else {
                logger.error("0 rows were updated!")
              }
            case Failure(exception) =>
              logger.error(s"There was an error inserting members for pool ${poolTag}", exception)
          }
         // val nextgEpoch = dr.nextMembers.head.g_epoch
          val gEpochUpdate = db.run(Tables.PoolInfoTable.filter(_.poolTag === poolTag).map(i => i.gEpoch -> i.updated)
            .update(gEpoch -> LocalDateTime.now()))
          gEpochUpdate.onComplete {
            case Success(rows) =>
              if (rows > 0) {
                logger.info(s"$rows in the states table were updated to have new gEpoch ${gEpoch}")
              } else {
                logger.error("0 rows were updated!")
              }
            case Failure(exception) =>
              logger.error(s"There was an error updating the gEpoch for pool ${poolTag}", exception)
          }


          db.run(Tables.PoolBlocksTable
            .filter(b => b.poolTag === poolTag)
            .filter(b => b.gEpoch >= gEpoch && b.gEpoch < gEpoch + ConcurrentBoxLoader.PLASMA_BATCH_SIZE)
            .map(b => b.status -> b.updated)
            .update(PoolBlock.INITIATED -> LocalDateTime.now()))
          logger.info(s"Finished updating blocks ${dr.members.head.block} with epoch ${gEpoch} and its next ${PLASMA_BATCH_SIZE - 1} epochs for pool" +
            s" ${poolTag} and status INITIATED")

          db.run(Tables.StateHistoryTables ++= Tables.StateHistoryTables.fromTransforms(transforms, gEpoch, members.head.block))
          logger.info("Finished writing transforms to StateHistory Table!")

          StatsRecorder.writePoolBalances(poolTag, dr.poolBalanceStates, db)

          logger.info(s"Finished all updates for distribution of pool ${poolTag}")
        }
      case Failure(exception) =>
        logger.error("There was a fatal error while evaluating a distribution response!", exception)

    }
    distResponse
  }

  def constructStateGroup(batchSelection: BatchSelection, boxes: Seq[InputBox]): Future[ConstructedDist[_ <: StateBalance]] = {
    implicit val timeout: Timeout = Timeout(1000 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    val collectedComponents = {
      val poolTag = batchSelection.info.poolTag
      val poolInfo = batchSelection.info
      val block = batchSelection.blocks.head

      val poolStateResp = (db.run(Tables.PoolStatesTable.filter(_.subpool === poolTag).result)).mapTo[Seq[PoolState]]
      val fPlacements = (db.run(Tables.PoolPlacementsTable.filter(p => p.subpool === poolTag && p.block === block.blockheight).result)).mapTo[Seq[PoolPlacement]]

      val initBlocks = Await.result((db.run(Tables.PoolBlocksTable.filter(b => b.status === PoolBlock.INITIATED && b.poolTag === block.poolTag).result)), 100 seconds)
      require(initBlocks.isEmpty, s"Initiated batches already exist for pool ${poolTag}!")

      val distComponents = for{
        states <- poolStateResp
        placements <- fPlacements
      } yield {

        // TODO: Removed for right now, exercise caution
        val gEpoch = poolInfo.g_epoch

        // TODO: Make these trys in order to prevent whole group failure when multiple groups from same pool are used
        require(states.head.status != PoolState.FAILURE, "A failed state exists!")
        require(states.head.status == PoolState.CONFIRMED, "The pool state is unconfirmed!")
        val constructDistResp = {

          require(placements.nonEmpty, s"No placements found for block ${block.blockheight}")

          val modifiedPlacements = modifyPlacements(placements)
          logger.info(s"Constructing distributions for block ${block.blockheight}")
          logger.info(s"Placements gEpoch: ${modifiedPlacements.head.g_epoch}, block: ${block.gEpoch}, poolInfo gEpoch: ${gEpoch}")
          logger.info(s"Current epochs in batch: ${batchSelection.blocks.map(_.gEpoch).toArray.mkString("Array(", ", ", ")")}")
          logger.info(s"Current blocks in batch: ${batchSelection.blocks.map(_.blockheight).toArray.mkString("Array(", ", ", ")")}")
          require(modifiedPlacements.head.g_epoch == block.gEpoch, "gEpoch was incorrect for these placements, maybe this is a future placement?")
          val balanceState = PaymentRouter.routeBalanceState(batchSelection.info)
          stateHandler ? DistConstructor(states.head, boxes, batchSelection, balanceState, modifiedPlacements)
        }

        constructDistResp.map {
          case constDist: ConstructedDist[_] =>
            constDist
          case failure: StateFailure =>
            logger.warn(s"A StateFailure was returned after DistConstruction!")
            logger.error("Ending distribution due to fatal state failure!")
            throw new Exception("Dist construction failed!")
          case _ =>
            logger.error("There was a fatal error during Distribution Construction")
            throw new Exception("An unexpected type was returned during Distribution Construction!")
        }
      }
      distComponents.flatten
    }
    collectedComponents
  }

  def modifyPlacements(placements: Seq[PoolPlacement]): Seq[PoolPlacement] = {
    placements.head.subpool match {
      case "f0f3581ea3aacf37c819f0f18a47585866aaf4c273d0c3c189d79b7d5fc71e80" =>
        distinctPlacements(placements)
      case "198999881b270fa41546ba3fb339d24c24914fbbf11a8283e4c879d6e30770b0" =>
        mergePlacements(placements)
      case _ =>
        distinctPlacements(placements)
    }
  }

  def mergePlacements(placements: Seq[PoolPlacement]): Seq[PoolPlacement] = {
    val mergedPlacements = {
      placements.foldLeft(Seq[PoolPlacement]()){
        (z, b) =>
          val existing = z.indexWhere(p => p.miner == b.miner)
          if(existing != -1){
            val currentPlacement = z(existing)
            z.updated(existing, currentPlacement.copy(
              amount = currentPlacement.amount + b.amount,
              amountTwo = currentPlacement.amountTwo.flatMap(l => b.amountTwo.map(l2 => l2 + l))
            ))
          }else{
            z ++ Seq(b)
          }
      }
    }
    mergedPlacements
  }

  def distinctPlacements(placements: Seq[PoolPlacement]): Seq[PoolPlacement] = {
    placements.foldLeft(Seq[PoolPlacement]()){
      (z, b) =>
        val existing = z.find(p => p.miner == b.miner)
        if(existing.isDefined){
          z
        }else{
          z ++ Seq(b)
        }
    }
  }

  // TODO: Currently vertically scaled, consider horizontal scaling with Seq[BatchSelections]
  def collectInputs(batchSelection: BatchSelection): Seq[InputBox] = {
    if(batchSelection.info.currency == PoolInformation.CURR_ERG) {
      val blockSum = Helpers.ergToNanoErg(batchSelection.blocks.map(_.reward).sum) + (Helpers.OneErg).toLong
      boxLoader.collectFromLoaded(blockSum).toSeq
    }else{
      val blockSum = Helpers.OneErg * 3
      boxLoader.collectFromLoaded(blockSum).toSeq
    }
  }

}
