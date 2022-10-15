package io.getblok.subpooling_core
package states.groups

import global.AppParameters.NodeWallet
import persistence.models.PersistenceModels.PoolMember
import plasma.StateConversions.balanceConversion
import plasma.{BalanceState, PoolBalanceState, SingleBalance}
import states.{DesyncedPlasmaException, StateTransformer}
import states.groups.PayoutGroup.GroupInfo
import states.models.CommandTypes.{INSERT, PAYOUT, UPDATE}
import states.models._
import states.transforms.InsertTransform
import states.transforms.singular.{PayoutTransform, SetupTransform, UpdateTransform}

import io.getblok.subpooling_core.states.transforms.singular_tokens.{TokenPayoutTransform, TokenSetupTransform, TokenUpdateTransform}
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.{BlockchainContext, ErgoId, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import special.sigma.AvlTree

import java.time.LocalDateTime
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class TokenPayoutGroup(ctx: BlockchainContext, wallet: NodeWallet, miners: Seq[PlasmaMiner], poolBox: InputBox, inputBoxes: Seq[InputBox],
                       balanceState: BalanceState[SingleBalance],  gEpoch: Long, block: Long, poolTag: String, holdingBox: InputBox,
                       tokenId: ErgoId, tokenName: String, commandBatch: Option[CommandBatch] = None)
                  extends StateGroup[SingleBalance] {

  val initState: TokenState = TokenState(poolBox, balanceState, inputBoxes, tokenId)
  var currentState: TokenState = initState
  val transformer: StateTransformer[SingleBalance] = new StateTransformer(ctx, initState)
  val setupState: CommandState = CommandState(poolBox, miners, CommandTypes.SETUP, -1)

  final val MINER_BATCH_SIZE = 150

  private val logger: Logger = LoggerFactory.getLogger("TokenPayoutGroup")

  val infoBuffer: ArrayBuffer[GroupInfo] = ArrayBuffer()
  var commandQueue: Seq[CommandState] = _
  override var transformResults: Seq[Try[TransformResult[SingleBalance]]] = Seq.empty[Try[TransformResult[SingleBalance]]]

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

  override def sendTransactions: Seq[Try[TransformResult[SingleBalance]]] = {
    transformResults = transformer.execute()
    logger.info("=========================================================")
    logger.info(s"FINAL PERSISTENT DIGEST: ${balanceState.map.toString()}")
    transformResults
  }

  def insertTx(commandState: CommandState): Unit = {
    logger.info(s"Applying insertion for ${commandState.data.length} miners")
    val insertTransform = InsertTransform[SingleBalance](ctx, wallet, commandState)
    val result = transformer.apply(insertTransform)
    currentState = result.nextState.asInstanceOf[TokenState]
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(INSERT, result.transaction.getId, result.transaction.getCost,
      result.transaction.toBytes.length, result.manifest.get.digestString)
  }

  def updateTx(commandState: CommandState): Unit = {
    logger.info(s"Applying update for ${commandState.data.length} miners")
    val updateTransform = TokenUpdateTransform(ctx, wallet, commandState)
    val result = transformer.apply(updateTransform)
    currentState = result.nextState.asInstanceOf[TokenState]
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(UPDATE, result.transaction.getId, result.transaction.getCost,
      result.transaction.toBytes.length, result.manifest.get.digestString)
  }

  def payoutTx(commandState: CommandState): Unit = {
    logger.info(s"Applying payout for ${commandState.data.length} miners")
    val payoutTransform = TokenPayoutTransform(ctx, wallet, commandState)
    val result = transformer.apply(payoutTransform)

    currentState = result.nextState.asInstanceOf[TokenState]
    transformResults = transformResults ++ Seq(Try(result))
    infoBuffer += GroupInfo(PAYOUT, result.transaction.getId, result.transaction.getCost,
      result.transaction.toBytes.length, result.manifest.get.digestString)
  }

  override def setup(): Unit = {
    logger.info("Now setting up token payout group")
    logger.info(s"Current digest: ${balanceState.map.toString}")
    val realDigest = Hex.toHexString(currentState.box.getRegisters.get(0).getValue.asInstanceOf[AvlTree].digest.toArray)
    if(realDigest != balanceState.map.toString()) {
      logger.error(s"${Hex.toHexString(currentState.box.getRegisters.get(0).getValue.asInstanceOf[AvlTree].digest.toArray)} != ${balanceState.map.toString()}")
      logger.error(s"Plasma is desynced for pool ${currentState.poolTag}!")
      throw DesyncedPlasmaException(currentState.poolTag, balanceState.map.toString(), realDigest)
    }
    balanceState.map.initiate()
    logger.info("Balance state initiated!")

    val setupTransform = TokenSetupTransform(ctx, wallet, setupState, MINER_BATCH_SIZE, holdingBox, commandBatch)
    transformer.apply(setupTransform)
    commandQueue = setupTransform.commandQueue
    logger.info(s"Token Payout group setup with ${commandQueue.length} commands")
  }

  def getMembers: Seq[PoolMember] = {
    val noState = balanceState.map.getTempMap.isEmpty
    if(noState)
      balanceState.map.initiate()
    val lookupMiners = miners zip balanceState.map.lookUp(miners.map(_.toStateMiner.toPartialStateMiner): _*).response

    if(noState)
      balanceState.map.dropChanges()
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
        val updateTransform = minerTransforms.find(_.command == CommandTypes.UPDATE)

        val transform = {
          payoutTransform match {
            case Some(value) => value
            case None => updateTransform.get
          }
        }

        morphMember(m, payoutTransform.getOrElse(transform))
    }

  }

  def getPoolBalanceStates: Seq[PoolBalanceState] = {
    val noState = balanceState.map.getTempMap.isEmpty
    if(noState)
      balanceState.map.initiate()

    val lookupMiners = miners zip balanceState.map.lookUp(miners.map(_.toStateMiner.toPartialStateMiner): _*).response

    if(noState)
      balanceState.map.dropChanges()

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


  def morphMember(miner: PlasmaMiner, transformResult: TransformResult[SingleBalance]): PoolMember = {
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

  def morphPoolBalanceState(miner: PlasmaMiner, transformResult: TransformResult[SingleBalance]): PoolBalanceState = {
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




