package utils

import org.ergoplatform.appkit.BoxOperations.IUnspentBoxesLoader
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoToken, InputBox}

import java.util
import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters.seqAsJavaListConverter

/**
 * Box loader allowing for parallelized box selection from a single-preloaded ConcurrentLinkedQueue
 * Returns a single box per page
 */

class ConcurrentBoxLoader(boxQueue: ConcurrentLinkedQueue[InputBox]) extends IUnspentBoxesLoader{
  override def prepare(ctx: BlockchainContext, addresses: util.List[Address], grossAmount: Long, tokensToSpend: util.List[ErgoToken]): Unit = {

  }

  override def prepareForAddress(address: Address): Unit = {
  }

  override def loadBoxesPage(ctx: BlockchainContext, address: Address, page: Integer): util.List[InputBox] = {
    Seq().asJava
  }
}
