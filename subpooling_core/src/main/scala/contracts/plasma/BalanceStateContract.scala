package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.getblok_plasma.collections.Proof
import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, PartialStateMiner, ShareState, StateBalance, StateMiner, StateScore}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import scorex.crypto.authds.avltree.batch.Insert
import sigmastate.Values
import sigmastate.eval.Colls


case class BalanceStateContract(contract: ErgoContract){
  import BalanceStateContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = script
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}

object BalanceStateContract {
  private val constants = ConstantsBuilder.create().build()
  val script: String = Scripts.BALANCE_STATE_SCRIPT
  private val logger: Logger = LoggerFactory.getLogger("BalanceStateContract")
  def generateStateContract(ctx: BlockchainContext): ErgoContract = {
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  def buildStateBox(ctx: BlockchainContext, balanceState: BalanceState, optValue: Option[Long] = None): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .registers(balanceState.map.ergoValue)
      .contract(generateStateContract(ctx))
      .build()
  }

  def buildRewardBox(ctx: BlockchainContext, value: Long, initReward: Long, contract: ErgoContract): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(value)
      .registers(ErgoValue.of(initReward))
      .contract(contract)
      .build()
  }

  def buildFeeBox(ctx: BlockchainContext, value: Long, contract: ErgoContract): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(value)
      .contract(contract)
      .build()
  }

  def applyInsertionContextVars(stateBox: InputBox, balanceState: BalanceState, inserts: Seq[PartialStateMiner]): InputBox = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(inserts.map(u => u -> StateBalance(0L)).toArray.map(u => u._1.toColl -> u._2.toColl)
    )(insertType.getRType), insertType)
    val result = balanceState.map.insert(inserts.map(u => u -> StateBalance(0L)):_*)
    logger.info(s"Inserting ${inserts.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue),
      ContextVar.of(2.toByte, ErgoValue.of(Colls.fromArray(Array(0.toByte)), ErgoType.byteType())))
  }

  def applyUpdateContextVars(stateBox: InputBox, balanceState: BalanceState, balanceChanges: Seq[(PartialStateMiner, StateBalance)]): (InputBox, Long) = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(balanceChanges.map(u => u._1.toColl -> u._2.toColl).toArray
    )(insertType.getRType), insertType)
    val result = balanceState.map.update(balanceChanges:_*)
    logger.info(s"${balanceChanges.head.toString()}")
    logger.info(s"Updating ${balanceChanges.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue),
      ContextVar.of(2.toByte, ErgoValue.of(Colls.fromArray(Array(1.toByte)), ErgoType.byteType()))) -> balanceChanges.map(_._2.balance).sum
  }

  def applyPayoutContextVars(stateBox: InputBox, balanceState: BalanceState, payouts: Seq[StateMiner]) = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val lastBalances = balanceState.map.lookUp((payouts.map(_.toPartialStateMiner)):_*).response.map(_.tryOp.get)
    val lastBalanceMap = payouts.indices.map(i => payouts(i) -> lastBalances(i))
    val nextBalanceMap = payouts.map(u => u.toPartialStateMiner -> StateBalance(0L))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(nextBalanceMap.map(u => u._1.toColl -> u._2.toColl).toArray
    )(insertType.getRType), insertType)
    val result = balanceState.map.update(nextBalanceMap:_*)
    logger.info(s"${nextBalanceMap.head.toString()}")
    logger.info(s"Updating ${nextBalanceMap.length} balance states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue),
      ContextVar.of(2.toByte, ErgoValue.of(Colls.fromArray(Array(2.toByte)), ErgoType.byteType()))) -> lastBalanceMap
  }

  def buildPaymentBoxes(ctx: BlockchainContext, payouts: Seq[(StateMiner, StateBalance)]): Seq[OutBox] = {
    for(u <- payouts) yield {
      logger.info(s"Balance: ${u._2}")
      ctx.newTxBuilder().outBoxBuilder()
        .value( u._2.balance )
        .contract(u._1.address.toErgoContract)
        .build()
    }
  }
}



