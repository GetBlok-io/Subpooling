package utils

import actors.QuickDbReader.{QueryAllSubPools, QueryPoolInfo}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.{EIP27Constants, Helpers}
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolBlock, PoolInformation}
import models.DatabaseModels.SPoolBlock
import org.ergoplatform.appkit.BoxOperations.IUnspentBoxesLoader
import org.ergoplatform.appkit.{Address, BlockchainContext, BoxOperations, ErgoClient, ErgoToken, InputBox}
import org.ergoplatform.wallet.boxes.BoxSelector
import org.slf4j.{Logger, LoggerFactory}
import utils.ConcurrentBoxLoader.{BLOCK_BATCH_SIZE, BatchSelection, BlockSelection}

import java.util
import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

/**
 * Box loader allowing for parallelized box selection from a single-preloaded ConcurrentLinkedQueue
 * Returns a single box per page
 */

class ConcurrentBoxLoader(query: ActorRef, ergoClient: ErgoClient, params: ParamsConfig, contexts: Contexts, wallet: NodeWallet) {

  val logger: Logger = LoggerFactory.getLogger("ConcurrentBoxLoader")
  val loadedBoxes: ConcurrentLinkedQueue[InputBox] = new ConcurrentLinkedQueue[InputBox]()


  def selectBlocks(blocks: Seq[SPoolBlock], strictBatch: Boolean): BatchSelection = {
    implicit val timeout: Timeout = Timeout(20 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    logger.info("Now selecting blocks with unique pool tags")

//    if(distinctOnly) {
//      val distinctBlocks = ArrayBuffer.empty[SPoolBlock]
//      for (block <- blocks.sortBy(b => b.gEpoch)) {
//        if (!distinctBlocks.exists(b => block.poolTag == b.poolTag)) {
//          logger.info(s"Unique pool tag ${block.poolTag} was added to selection!")
//          distinctBlocks += block
//        }
//      }
//      distinctBlocks.toSeq.take(params.pendingBlockNum)
//        .map(db => BlockSelection(db, Await.result((query ? QueryPoolInfo(db.poolTag)).mapTo[PoolInformation], timeout.duration)))
//    }else{
//      blocks.take(params.pendingBlockNum)
//        .map(pb => BlockSelection(pb, Await.result((query ? QueryPoolInfo(pb.poolTag)).mapTo[PoolInformation], timeout.duration)))
//    }
    var blockList: ArrayBuffer[SPoolBlock] = ArrayBuffer(blocks:_*)
    var firstBlock: Option[SPoolBlock] = None

    while(firstBlock.isEmpty){
      require(blockList.nonEmpty, s"No pools found with ${BLOCK_BATCH_SIZE} blocks")
      val tryHead = blockList.head
      val poolBlocks = blockList.filter(_.poolTag == tryHead.poolTag)
      if(poolBlocks.size < BLOCK_BATCH_SIZE && strictBatch){
        logger.info(s"Removing pool ${tryHead.poolTag} from selection due to lacking ${BLOCK_BATCH_SIZE} confirmed blocks")
        blockList --= poolBlocks
      }else{
        firstBlock = Some(blockList.head)
      }
    }
    val blockHead = firstBlock.get
    var poolBlocks = blocks.filter(_.poolTag == blockHead.poolTag).sortBy(_.gEpoch).take(BLOCK_BATCH_SIZE)
    logger.info(s"Current pool being paid out: ${blockHead.poolTag}")
    val poolInfo =  Await.result((query ? QueryPoolInfo(blockHead.poolTag)).mapTo[PoolInformation], timeout.duration)
    logger.info(s"With payment type ${poolInfo.payment_type}")

    BatchSelection(poolBlocks, poolInfo)
  }
  @deprecated
  def makeBlockBoxMap(blockSelections: Seq[BlockSelection], collectedInputs: ArrayBuffer[InputBox], maxInputs: Long): Map[Long, Seq[InputBox]] = {
    var blockAmountMap = Map.empty[Long, Seq[InputBox]]
    for (blockSel <- blockSelections) {
      var blockAmount = 0L
      var inputsForBlock = collectedInputs.indices.takeWhile {
        idx =>
          blockAmount = blockAmount + collectedInputs(idx).getValue
          // Not final, so keep iterating
          if (blockAmount < maxInputs) {
            true
          } else {
            // This index is final box needed, so return true one more time
            if (blockAmount - collectedInputs(idx).getValue <= maxInputs) {
              true
            } else {
              // This box is not needed to be greater than max, now returning
              blockAmount = blockAmount - collectedInputs(idx).getValue
              false
            }
          }
      }.map(idx => collectedInputs(idx))
      logger.info(s"Total of ${inputsForBlock.size} boxes with $blockAmount value")
      collectedInputs --= inputsForBlock
      logger.info("Adding block and boxes to map")
      blockAmountMap = blockAmountMap + (blockSel.block.blockheight -> inputsForBlock.toSeq)
    }
    blockAmountMap
  }

  def preLoadInputBoxes(amountToFind: Long): ConcurrentLinkedQueue[InputBox] = {
    logger.info(s"Now preLoading input boxes with a total of ${Helpers.nanoErgToErg(amountToFind)} ERG")
    val collectedInputs = ArrayBuffer() ++ ergoClient.execute {
      ctx =>
        wallet.boxes(ctx, amountToFind).get
    }.asScala.toSeq.sortBy(b => b.getValue.toLong).reverse
    collectedInputs.foreach{
      ib => loadedBoxes.add(ib)
    }
    logger.info(s"Added ${collectedInputs.length} boxes to ConcurrentBoxLoader, with total value of" +
      s" ${Helpers.nanoErgToErg(collectedInputs.map(_.getValue.toLong).sum)} ERG")
    loadedBoxes
  }

  def collectFromLoaded(amountToCollect: Long): ArrayBuffer[InputBox] = {
    var currentSum = 0L
    val boxesCollected = ArrayBuffer.empty[InputBox]
    while(currentSum < amountToCollect){
      val polledBox = loadedBoxes.poll()
      boxesCollected += polledBox
      currentSum = currentSum + polledBox.getValue.toLong
      if(polledBox.getTokens.size() > 0){
        if(polledBox.getTokens.get(0).getId == EIP27Constants.REEM_TOKEN){
          logger.info(s"Subtracting ${Helpers.nanoErgToErg(polledBox.getTokens.get(0).getValue)} ERG to conform to EIP-27 rules")
          currentSum = currentSum - polledBox.getTokens.get(0).getValue // Subtract out Re-em tokens during box selection
        }
      }
    }

    logger.info(s"Collected ${boxesCollected.length} boxes for total value of ${currentSum}. The value needed was ${amountToCollect}")


    boxesCollected
  }
}
object ConcurrentBoxLoader {
  final val BLOCK_BATCH_SIZE = 5


  case class PartialBlockSelection(block: SPoolBlock, poolTag: String)
  case class BlockSelection(block: SPoolBlock, poolInformation: PoolInformation)
  case class BatchSelection(blocks: Seq[SPoolBlock], info: PoolInformation)
}
