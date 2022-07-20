package io.getblok.subpooling_core
package states.groups

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolMember, PoolPlacement}
import io.getblok.subpooling_core.plasma.{BalanceState, PoolBalanceState}
import io.getblok.subpooling_core.states.StateTransformer
import io.getblok.subpooling_core.states.groups.PayoutGroup.GroupInfo
import io.getblok.subpooling_core.states.models.CommandTypes.{Command, INSERT, PAYOUT, UPDATE}
import io.getblok.subpooling_core.states.models.{CommandState, CommandTypes, PlasmaMiner, State, TransformResult}
import io.getblok.subpooling_core.states.transforms.{InsertTransform, PayoutTransform, SetupTransform, UpdateTransform}
import org.ergoplatform.appkit.{BlockchainContext, InputBox}
import org.slf4j.{Logger, LoggerFactory}

import java.time.LocalDateTime
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class PayoutGroup(ctx: BlockchainContext, wallet: NodeWallet, miners: Seq[PlasmaMiner], poolBox: InputBox, inputBoxes: Seq[InputBox],
                  balanceState: BalanceState, gEpoch: Long, block: Long, poolTag: String, fee: Long, reward: Long) extends StateGroup {
  val initState: State = State(poolBox, balanceState, inputBoxes)
  var currentState: State = initState
  val transformer: StateTransformer = new StateTransformer(ctx, initState)
  val setupState: CommandState = CommandState(poolBox, miners, CommandTypes.SETUP, -1)

  final val MINER_BATCH_SIZE = 150

  private val logger: Logger = LoggerFactory.getLogger("PayoutGroup")

  val infoBuffer: ArrayBuffer[GroupInfo] = ArrayBuffer()
  var commandQueue: Seq[CommandState] = _
  override var transformResults: Seq[Try[TransformResult]] = Seq.empty[Try[TransformResult]]

  override def applyTransformations(): Try[Unit] = {
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

  override def sendTransactions: Seq[Try[TransformResult]] = {
    transformResults = transformer.execute()
    transformResults
  }

  def insertTx(commandState: CommandState): Unit = {
    logger.info(s"Applying insertion for ${commandState.data.length} miners")
    val insertTransform = InsertTransform(ctx, wallet, commandState)
    val result = transformer.apply(insertTransform)
    currentState = result.nextState
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(INSERT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
  }

  def updateTx(commandState: CommandState): Unit = {
    logger.info(s"Applying update for ${commandState.data.length} miners")
    val updateTransform = UpdateTransform(ctx, wallet, commandState)
    val result = transformer.apply(updateTransform)
    currentState = result.nextState
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(UPDATE, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
  }

  def payoutTx(commandState: CommandState): Unit = {
    logger.info(s"Applying payout for ${commandState.data.length} miners")
    val payoutTransform = PayoutTransform(ctx, wallet, commandState)
    val result = transformer.apply(payoutTransform)

    currentState = result.nextState
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(PAYOUT, result.transaction.getId, result.transaction.getCost, result.transaction.toBytes.length)
  }

  override def setup(): Unit = {
    logger.info("Now setting up payout group")
    val setupTransform = SetupTransform(ctx, wallet, setupState, MINER_BATCH_SIZE, fee, reward)
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

        morphMember(m, payoutTransform.getOrElse(updateTransform))
    }

  }

  def getPoolBalanceStates: Seq[PoolBalanceState] = {
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

        morphPoolBalanceState(m, payoutTransform.getOrElse(updateTransform))
    }

  }


  def morphMember(miner: PlasmaMiner, transformResult: TransformResult): PoolMember = {
    transformResult.command match {
      case CommandTypes.PAYOUT =>
        PoolMember(
            poolTag, 0L, transformResult.transaction.getId.replace("\"", ""),
          currentState.box.getId.toString, gEpoch, gEpoch, currentState.box.getCreationHeight, miner.miner.toString,
          miner.shareScore, miner.shareNum, miner.sharePerc, miner.minPay, 0L, miner.balance, miner.amountAdded,
          miner.epochsMined, "none", 0L, block, LocalDateTime.now()
        )
      case CommandTypes.UPDATE =>
        PoolMember(
          poolTag, 0L, transformResult.transaction.getId.replace("\"", ""),
          currentState.box.getId.toString, gEpoch, gEpoch, currentState.box.getCreationHeight, miner.miner.toString,
          miner.shareScore, miner.shareNum, miner.sharePerc, miner.minPay, miner.balance, 0L, miner.amountAdded,
          miner.epochsMined, "none", 0L, block, LocalDateTime.now()
        )
    }
  }

  def morphPoolBalanceState(miner: PlasmaMiner, transformResult: TransformResult): PoolBalanceState = {
    transformResult.command match {
      case CommandTypes.PAYOUT =>
        PoolBalanceState(
          poolTag, gEpoch, transformResult.transaction.getId.replace("\"", ""),
          transformResult.manifest.get.digestString, transformResult.step, transformResult.command.toString,
          miner.miner.toString, miner.toStateMiner.hexString, 0L, miner.balance, block, LocalDateTime.now(),
          LocalDateTime.now()
        )
      case CommandTypes.UPDATE =>
        PoolBalanceState(
          poolTag, gEpoch, transformResult.transaction.getId.replace("\"", ""),
          transformResult.manifest.get.digestString, transformResult.step, transformResult.command.toString,
          miner.miner.toString, miner.toStateMiner.hexString, miner.balance, 0L, block, LocalDateTime.now(),
          LocalDateTime.now()
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
  case class GroupInfo(transform: Command, txId: String, cost: Long, txSize: Long){
    override def toString: String = s"${transform}: ${txId} ->  ${cost} tx cost -> ${txSize} bytes"
  }
}
