package io.getblok.subpooling_core
package states

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.plasma.StateBalance
import io.getblok.subpooling_core.states.models.{SingleState, State, StateTransition, TransformResult}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}
import org.slf4j.{Logger, LoggerFactory}
import scorex.crypto.authds.ADDigest

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


class StateTransformer[T <: StateBalance](ctx: BlockchainContext, initState: State[T]) {
  val txQueue: mutable.Queue[TransformResult[T]] = mutable.Queue.empty[TransformResult[T]]
  var currentState: State[T] = initState
  val initDigest: ADDigest = initState.digest
  private val logger: Logger = LoggerFactory.getLogger("StateTransformer")

  def apply(transformation: StateTransition[T]): TransformResult[T] = {
    logger.info(s"Applying transformation for ${transformation.commandState.data.length} miners")
    val transform = transformation.transform(currentState)

    if(transform.isSuccess){
      logger.info(s"Transformation was successful with id ${transform.get.transaction.getId}")

      logger.info("Now adding to transaction queue and updating current state")

      txQueue.enqueue(transform.get)
      currentState = transform.get.nextState
      transform.get
    }else{
      logger.error("A state transformation failed!")
      logger.error(s"Exception occurred due to: ", transform.failed.get)
      logger.error(s"Now rolling back to initial state digest")
      revert()
      throw new StateTransformationException
    }

  }

  def execute(): Seq[Try[TransformResult[T]]] = {
    val versionStack: mutable.ArrayStack[ADDigest] = mutable.ArrayStack()
    var rolledBack = false
    versionStack.push(initDigest)

    txQueue.map{
      tResult =>
        logger.info(s"Now sending transaction ${tResult.transaction.getId}")
        val s = Try(ctx.sendTransaction(tResult.transaction))
        Thread.sleep(500)

        if(!rolledBack && s.isSuccess) {

          logger.info(s"Transaction with id ${s} was successfully sent!")
          versionStack.push(tResult.nextState.digest)
          currentState.balanceState.map.commitNextOperation()
          Success(tResult)

        } else {

          if(!rolledBack) {
            logger.error("A fatal error occurred while sending transactions", s.failed.get)
            val lastVers = versionStack.pop()
            logger.warn(s"Reverting back to last successful state with digest ${Hex.toHexString(lastVers)}")
            revert(Some(lastVers))
            rolledBack = true
          }else{
            logger.warn(s"Skipped sending transaction ${tResult.transaction.getId} due to previous rollback")
          }

          Failure(new TxSendException(tResult.transaction.getId))
        }
    }
  }

  def revert(version: Option[ADDigest] = None): Unit = {

    currentState.balanceState.map.dropChanges()

    logger.info("Successfully dropped uncommitted changes")
    logger.info(s"Digest to revert to: ${Hex.toHexString(version.getOrElse(initDigest))}")
    logger.info(s"Current digest: ${currentState.balanceState.map.toString()}")

  }
}
