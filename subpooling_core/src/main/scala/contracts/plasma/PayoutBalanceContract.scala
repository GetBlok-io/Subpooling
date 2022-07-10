package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract.logger
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, PartialStateMiner, StateBalance, StateMiner}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values
import sigmastate.eval.Colls

import scala.collection.immutable


case class PayoutBalanceContract(contract: ErgoContract) {
  import PayoutBalanceContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = script
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}
object PayoutBalanceContract {

  private val constants = ConstantsBuilder.create().build()
  val script: String = Scripts.PAYOUT_BALANCE_SCRIPT
  private val logger: Logger = LoggerFactory.getLogger("PayoutBalanceContract")
  def generatePayoutContract(ctx: BlockchainContext): ErgoContract = {
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  def buildPayoutBox(ctx: BlockchainContext, optValue: Option[Long] = None): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .contract(generatePayoutContract(ctx))
      .build()
  }

  def applyPayoutContextVars(stateBox: InputBox, balanceState: BalanceState, payouts: Seq[StateMiner]): (InputBox, immutable.IndexedSeq[(StateMiner, StateBalance)]) = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val lastBalances = balanceState.map.lookUp((payouts.map(_.toPartialStateMiner)):_*).response.map(_.opt.get)
    val lastBalanceMap = payouts.indices.map(i => payouts(i) -> lastBalances(i))
    val nextBalanceMap = payouts.map(u => u.toPartialStateMiner -> StateBalance(0L))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(nextBalanceMap.map(u => u._1.toColl -> u._2.toColl).toArray
    )(insertType.getRType), insertType)
    val result = balanceState.map.update(nextBalanceMap:_*)
    logger.info(s"${nextBalanceMap.head.toString()}")
    logger.info(s"Updating ${nextBalanceMap.length} balance states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue)) -> lastBalanceMap
  }
}





