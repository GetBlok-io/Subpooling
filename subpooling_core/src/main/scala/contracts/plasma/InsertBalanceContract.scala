package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, PartialStateMiner, StateBalance, StateMiner}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values
import sigmastate.eval.Colls


case class InsertBalanceContract(contract: ErgoContract) {
  import InsertBalanceContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = script
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}

object InsertBalanceContract {
  private val constants = ConstantsBuilder.create().build()
  val script: String = Scripts.INSERT_BALANCE_SCRIPT
  private val logger: Logger = LoggerFactory.getLogger("BalanceStateContract")
  
  def generate(ctx: BlockchainContext): ErgoContract = {
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  def buildBox(ctx: BlockchainContext, optValue: Option[Long] = None): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .contract(generate(ctx))
      .build()
  }

  def applyContext(updateBox: InputBox, balanceState: BalanceState, inserts: Seq[PartialStateMiner]): InputBox = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(inserts.map(u => u -> StateBalance(0L)).toArray.map(u => u._1.toColl -> u._2.toColl)
    )(insertType.getRType), insertType)
    val result = balanceState.map.insert(inserts.map(u => u -> StateBalance(0L)):_*)
    logger.info(s"Inserting ${inserts.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    updateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue))
  }


}




