package io.getblok.subpooling_core
package states

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.states.models.{State, StateTransition, TransformResult}
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


class StateTransformer(ctx: BlockchainContext, initState: State) {
  val txQueue: mutable.Queue[TransformResult] = mutable.Queue.empty[TransformResult]
  var currentState: State = initState
  private val logger: Logger = LoggerFactory.getLogger("StateTransformer")

  def apply(transformation: StateTransition): TransformResult = {
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

      throw new StateTransformationException
    }

  }

  def execute(): Seq[Try[TransformResult]] = {

    txQueue.map{
      tResult =>

        val s = Try(ctx.sendTransaction(tResult.transaction))

        if(s.isSuccess) {
          logger.info(s"Transaction with id ${s} was successfully sent!")
          Success(tResult)
        } else {
          logger.error("A fatal error occurred while sending transactions", s.failed.get)
          val exception = new TxSendException(tResult.transaction.getId)
          Failure(exception)
        }
    }
  }
}
