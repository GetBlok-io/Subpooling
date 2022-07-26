package io.getblok.subpooling_core
package states.groups

import global.AppParameters.NodeWallet
import persistence.models.PersistenceModels.PoolMember
import plasma.{BalanceState, DualBalance, PoolBalanceState, SingleBalance}
import states.StateTransformer
import states.groups.PayoutGroup.GroupInfo
import states.models.CommandTypes.{DELETE, INSERT, PAYOUT, UPDATE}
import states.models._
import states.transforms.singular.{PayoutTransform, SetupTransform, UpdateTransform}

import io.getblok.subpooling_core.plasma.StateConversions.dualBalanceConversion
import io.getblok.subpooling_core.states.transforms.{DeleteTransform, InsertTransform}
import io.getblok.subpooling_core.states.transforms.dual.{DualPayoutTransform, DualSetupTransform, DualUpdateTransform}
import org.ergoplatform.appkit.{BlockchainContext, ErgoId, InputBox}
import org.slf4j.{Logger, LoggerFactory}

import java.time.LocalDateTime
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class DualGroup(ctx: BlockchainContext, wallet: NodeWallet, miners: Seq[PlasmaMiner], poolBox: InputBox, inputBoxes: Seq[InputBox],
                balanceState: BalanceState[DualBalance], gEpoch: Long, block: Long, poolTag: String, holdingBox: InputBox,
                tokenId: ErgoId, tokenName: String) extends StateGroup[DualBalance] {
  val initState: DualState = DualState(poolBox, balanceState, inputBoxes, tokenId)
  var currentState: DualState = initState
  val transformer: StateTransformer[DualBalance] = new StateTransformer(ctx, initState)
  val setupState: CommandState = CommandState(poolBox, miners, CommandTypes.SETUP, -1)

  final val MINER_BATCH_SIZE = 150

  private val logger: Logger = LoggerFactory.getLogger("DualPayoutGroup")

  val infoBuffer: ArrayBuffer[GroupInfo] = ArrayBuffer()
  var commandQueue: Seq[CommandState] = _
  override var transformResults: Seq[Try[TransformResult[DualBalance]]] = Seq.empty[Try[TransformResult[DualBalance]]]

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
              case CommandTypes.DELETE =>
                deleteTx(cmdState)
            }
        }
      }
    }

    printInfo()
    applied
  }

  override def sendTransactions: Seq[Try[TransformResult[DualBalance]]] = {
    transformResults = transformer.execute()
    transformResults
  }

  def insertTx(commandState: CommandState): Unit = {
    logger.info(s"Applying insertion for ${commandState.data.length} miners")
    val insertTransform = InsertTransform[DualBalance](ctx, wallet, commandState)
    val result = transformer.apply(insertTransform)
    currentState = result.nextState.asInstanceOf[DualState]
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(INSERT, result.transaction.getId, result.transaction.getCost,
      result.transaction.toBytes.length, result.manifest.get.digestString)
  }

  def updateTx(commandState: CommandState): Unit = {
    logger.info(s"Applying update for ${commandState.data.length} miners")
    val updateTransform = DualUpdateTransform(ctx, wallet, commandState)
    val result = transformer.apply(updateTransform)
    currentState = result.nextState.asInstanceOf[DualState]
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(UPDATE, result.transaction.getId, result.transaction.getCost,
      result.transaction.toBytes.length, result.manifest.get.digestString)
  }

  def payoutTx(commandState: CommandState): Unit = {
    logger.info(s"Applying payout for ${commandState.data.length} miners")
    val payoutTransform = DualPayoutTransform(ctx, wallet, commandState)
    val result = transformer.apply(payoutTransform)

    currentState = result.nextState.asInstanceOf[DualState]
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(PAYOUT, result.transaction.getId, result.transaction.getCost,
      result.transaction.toBytes.length, result.manifest.get.digestString)
  }


  def deleteTx(commandState: CommandState): Unit = {
    val deleteTransform = DeleteTransform[DualBalance](ctx, wallet, commandState)
    val result = transformer.apply(deleteTransform)
    currentState = result.nextState.asInstanceOf[DualState]
    logger.info(s"${result.transaction.toJson(true)}")
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(DELETE, result.transaction.getId, result.transaction.getCost,
      result.transaction.toBytes.length, result.manifest.get.digestString)
  }

  override def setup(): Unit = {
    logger.info("Now setting up payout group")

    balanceState.map.initiate()
    logger.info("Initiated ProxyMap!")
    val setupTransform = DualSetupTransform(ctx, wallet, setupState, holdingBox, MINER_BATCH_SIZE)
    transformer.apply(setupTransform)
    commandQueue = setupTransform.commandQueue
    logger.info(s"Payout group setup with ${commandQueue.length} commands")
  }

  def getMembers: Seq[PoolMember] = {
    val lookupMiners = miners zip balanceState.map.lookUp(miners.map(_.toStateMiner.toPartialStateMiner): _*).response

    val updatedBalances = lookupMiners.map{
      m =>
        if(m._2.tryOp.get.get.balance == 0L && m._1.amountAdded > 0)
          m._1.copy(balance = m._1.balance + m._1.amountAdded, balanceTwo = m._1.balanceTwo + m._1.addedTwo)
        else
          m._1.copy(balance = m._2.tryOp.get.get.balance, balanceTwo = m._2.tryOp.get.get.balanceTwo)
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
          m._1.copy(balance = m._1.balance + m._1.amountAdded, balanceTwo = m._1.balanceTwo + m._1.addedTwo)
        else
          m._1.copy(balance = m._2.tryOp.get.get.balance, balanceTwo = m._2.tryOp.get.get.balanceTwo)
    }

    updatedBalances.map{
      m =>
        val minerTransforms = transformResults.filter(_.get.data.exists(d => d.miner == m.miner)).map(_.get)

        val payoutTransform = minerTransforms.find(_.command == CommandTypes.PAYOUT)
        val updateTransform = minerTransforms.find(_.command == CommandTypes.UPDATE).get

        morphPoolBalanceState(m, payoutTransform.getOrElse(updateTransform))
    }

  }


  def morphMember(miner: PlasmaMiner, transformResult: TransformResult[DualBalance]): PoolMember = {
    transformResult.command match {
      case CommandTypes.PAYOUT =>
        PoolMember(
            poolTag, 0L, transformResult.transaction.getId.replace("\"", ""),
          currentState.box.getId.toString, gEpoch, gEpoch, currentState.box.getCreationHeight, miner.miner.toString,
          miner.shareScore, miner.shareNum, miner.sharePerc, miner.minPay, 0L, miner.balance, miner.amountAdded,
          miner.epochsMined, s"${tokenName}", miner.balanceTwo, block, LocalDateTime.now()
        )
      case CommandTypes.UPDATE =>
        PoolMember(
          poolTag, 0L, transformResult.transaction.getId.replace("\"", ""),
          currentState.box.getId.toString, gEpoch, gEpoch, currentState.box.getCreationHeight, miner.miner.toString,
          miner.shareScore, miner.shareNum, miner.sharePerc, miner.minPay, miner.balance, 0L, miner.amountAdded,
          miner.epochsMined, s"${tokenName}", miner.balanceTwo, block, LocalDateTime.now()
        )
    }
  }

  def morphPoolBalanceState(miner: PlasmaMiner, transformResult: TransformResult[DualBalance]): PoolBalanceState = {
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




