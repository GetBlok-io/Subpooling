package io.getblok.subpooling_core
package states.groups

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.Models.{PoolMember, PoolPlacement}
import io.getblok.subpooling_core.plasma.BalanceState
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.groups.PayoutGroup.TestInfo
import io.getblok.subpooling_core.states.models.CommandTypes.{Command, INSERT, PAYOUT, UPDATE}
import io.getblok.subpooling_core.states.models.{CommandState, CommandTypes, PlasmaMiner, State, TransformResult}
import io.getblok.subpooling_core.states.transforms.{InsertTransform, PayoutTransform, SetupTransform, UpdateTransform}
import org.ergoplatform.appkit.{BlockchainContext, InputBox}
import org.slf4j.{Logger, LoggerFactory}

import java.time.LocalDateTime
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class PayoutGroup(ctx: BlockchainContext, wallet: NodeWallet, miners: Seq[PlasmaMiner], poolBox: InputBox, inputBoxes: Seq[InputBox],
                  balanceState: BalanceState, gEpoch: Long, block: Long, poolTag: String) {
  val initState: State = State(poolBox, balanceState, inputBoxes)
  var currentState: State = initState
  val transformer: StateTransformer = new StateTransformer(ctx, initState)
  val setupState: CommandState = CommandState(poolBox, miners, CommandTypes.SETUP)

  final val MINER_BATCH_SIZE = 150

  private val logger: Logger = LoggerFactory.getLogger("PayoutGroup")

  val infoBuffer: ArrayBuffer[TestInfo] = ArrayBuffer()
  var commandQueue: Seq[CommandState] = _
  var transformResults: Seq[Try[TransformResult]] = Seq.empty[Try[TransformResult]]

  def applyTransformations(): Try[Unit] = {
    val applied = {
      Try {
        commandQueue.foreach {
          cmdState =>
            cmdState.commandType match {
              case CommandTypes.INSERT =>
                insertTx(cmdState)
              case CommandTypes.UPDATE =>
                updateTx(cmdState)
              case CommandTypes.PAYOUT =>
                payoutTx(cmdState)
            }
        }
      }
    }

    printInfo()
    applied
  }

  def sendTransactions: Seq[Try[TransformResult]] = {
    transformResults = transformer.execute()
    transformResults
  }

  def insertTx(commandState: CommandState): Unit = {
    logger.info(s"Applying insertion for ${commandState.data.length} miners")
    val insertTransform = InsertTransform(ctx, wallet, commandState)
    val result = transformer.apply(insertTransform)
    currentState = result.nextState
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += TestInfo(INSERT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
  }

  def updateTx(commandState: CommandState): Unit = {
    logger.info(s"Applying update for ${commandState.data.length} miners")
    val updateTransform = UpdateTransform(ctx, wallet, commandState)
    val result = transformer.apply(updateTransform)
    currentState = result.nextState
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += TestInfo(UPDATE, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
  }

  def payoutTx(commandState: CommandState): Unit = {
    logger.info(s"Applying payout for ${commandState.data.length} miners")
    val payoutTransform = PayoutTransform(ctx, wallet, commandState)
    val result = transformer.apply(payoutTransform)

    currentState = result.nextState
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += TestInfo(PAYOUT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
  }

  def setup(): Unit = {
    logger.info("Now setting up payout group")
    val setupTransform = SetupTransform(ctx, wallet, setupState, MINER_BATCH_SIZE)
    transformer.apply(setupTransform)
    commandQueue = setupTransform.commandQueue
    logger.info(s"Payout group setup with ${commandQueue.length} commands")
  }

  def getMembers: Seq[PoolMember] = {
    val lookupMiners = miners zip balanceState.map.lookUp(miners.map(_.toStateMiner.toPartialStateMiner): _*).response

    val updatedBalances = lookupMiners.map{
      m =>
        if(m._2.tryOp.get.get.balance == 0L && m._1.amountAdded > 0)
          m._1.copy(balance = m._1.balance + m._1.amountAdded)
        else
          m._1.copy(balance = m._2.tryOp.get.get.balance)
    }

    updatedBalances.map{
      m =>
        val minerTransforms = transformResults.filter(_.get.data.exists(d => d.miner == m.miner)).map(_.get)

        val payoutTransform = minerTransforms.find(_.command == CommandTypes.PAYOUT)
        val updateTransform = minerTransforms.find(_.command == CommandTypes.UPDATE).get

        morph(m, payoutTransform.getOrElse(updateTransform))
    }

  }

  def morph(miner: PlasmaMiner, transformResult: TransformResult): PoolMember = {
    transformResult.command match {
      case CommandTypes.PAYOUT =>
        PoolMember(
          poolTag, 0L, transformResult.transaction.getId.replace("\"", ""),
          currentState.box.getId.toString, gEpoch, gEpoch, currentState.box.getCreationHeight, miner.miner.toString,
          miner.shareScore, miner.shareNum, miner.sharePerc, miner.minPay, 0L, miner.balance, miner.amountAdded,
          miner.epochsMined + 1, "none", 0L, block, LocalDateTime.now()
        )
      case CommandTypes.UPDATE =>
        PoolMember(
          poolTag, 0L, transformResult.transaction.getId.replace("\"", ""),
          currentState.box.getId.toString, gEpoch, gEpoch, currentState.box.getCreationHeight, miner.miner.toString,
          miner.shareScore, miner.shareNum, miner.sharePerc, miner.minPay, miner.balance, 0L, miner.amountAdded,
          miner.epochsMined + 1, "none", 0L, block, LocalDateTime.now()
        )
    }
  }

  def printInfo(): Unit = {
    logger.info("=============== Printing Group Info ===============")
    logger.info(s"TOTAL MINERS: ${miners.length}")
    logger.info(s"BATCH SIZE: ${MINER_BATCH_SIZE}")
    logger.info(s"AMOUNT ADDED: ${miners.map(_.amountAdded).sum}")
    logger.info(s"AMOUNT REMAINING: ${currentState.box.getValue}")
    for(ti <- infoBuffer){
      logger.info(ti.toString)
    }
  }
}



object PayoutGroup {
  case class TestInfo(transform: Command, txId: String, cost: Long, txSize: Long){
    override def toString: String = s"${transform}: ${txId} ->  ${cost} tx cost -> ${txSize} bytes"
  }
}
