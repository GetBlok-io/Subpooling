package utils

import actors.QuickDbReader.QueryPoolInfo
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.persistence.models.Models.{PoolBlock, PoolInformation}
import org.ergoplatform.appkit.BoxOperations.IUnspentBoxesLoader
import org.ergoplatform.appkit.{Address, BlockchainContext, BoxOperations, ErgoClient, ErgoToken, InputBox}
import org.ergoplatform.wallet.boxes.BoxSelector
import org.slf4j.{Logger, LoggerFactory}
import utils.ConcurrentBoxLoader.BlockSelection

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

class ConcurrentBoxLoader(query: ActorRef, ergoClient: ErgoClient, params: ParamsConfig, contexts: Contexts) {

  val logger: Logger = LoggerFactory.getLogger("ConcurrentBoxLoader")
  val loadedBoxes: ConcurrentLinkedQueue[InputBox] = new ConcurrentLinkedQueue[InputBox]()


  def selectBlocks(blocks: Seq[PoolBlock], distinctOnly: Boolean): Seq[BlockSelection] = {
    implicit val timeout: Timeout = Timeout(20 seconds)
    implicit val taskContext: ExecutionContext = contexts.taskContext
    logger.info("Now selecting blocks with unique pool tags")

    if(distinctOnly) {
      val distinctBlocks = ArrayBuffer.empty[PoolBlock]
      for (block <- blocks.sortBy(b => b.gEpoch)) {
        if (!distinctBlocks.exists(b => block.poolTag == b.poolTag)) {
          logger.info(s"Unique pool tag ${block.poolTag} was added to selection!")
          distinctBlocks += block
        }
      }
      distinctBlocks.toSeq.take(params.pendingBlockNum)
        .map(db => BlockSelection(db, Await.result((query ? QueryPoolInfo(db.poolTag)).mapTo[PoolInformation], timeout.duration)))
    }else{
      blocks.take(params.pendingBlockNum)
        .map(pb => BlockSelection(pb, Await.result((query ? QueryPoolInfo(pb.poolTag)).mapTo[PoolInformation], timeout.duration)))
    }
  }

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
    val collectedInputs = ArrayBuffer() ++ ergoClient.execute {
      ctx =>
        ctx.getWallet.getUnspentBoxes(amountToFind).get()
    }.asScala.toSeq.filter(_.getTokens.size() == 0).sortBy(b => b.getValue.toLong).reverse
    collectedInputs.foreach{
      ib => loadedBoxes.add(ib)
    }
    loadedBoxes
  }

  def collectFromLoaded(amountToCollect: Long): ArrayBuffer[InputBox] = {
    var currentSum = 0L
    val boxesCollected = ArrayBuffer.empty[InputBox]
    while(currentSum < amountToCollect){
      val polledBox = loadedBoxes.poll()
      boxesCollected += polledBox
      currentSum = currentSum + polledBox.getValue.toLong
    }


    logger.info(s"Collected ${boxesCollected.length} boxes for total value of ${currentSum}. The value needed was ${amountToCollect}")


    boxesCollected
  }
}
object ConcurrentBoxLoader {



  case class PartialBlockSelection(block: PoolBlock, poolTag: String)
  case class BlockSelection(block: PoolBlock, poolInformation: PoolInformation)
}
