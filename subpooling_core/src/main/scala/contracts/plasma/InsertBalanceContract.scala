package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, PartialStateMiner, SingleBalance, StateMiner}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoType, ErgoValue, InputBox, OutBox}
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
  val script: String = Scripts.INSERT_BALANCE_SCRIPT
  private val logger: Logger = LoggerFactory.getLogger("InsertBalanceContract")
  
  def generate(ctx: BlockchainContext, poolNFT: ErgoId): ErgoContract = {
    val constants = ConstantsBuilder.create().item("const_poolNFT", Colls.fromArray(poolNFT.getBytes)).build()
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  def buildBox(ctx: BlockchainContext, poolNFT: ErgoId, optValue: Option[Long] = None): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .contract(generate(ctx, poolNFT))
      .build()
  }

  def applySingleContext(updateBox: InputBox, balanceState: BalanceState[SingleBalance], inserts: Seq[PartialStateMiner]): InputBox = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(inserts.map(u => u -> SingleBalance(0L)).toArray.map(u => u._1.toColl -> u._2.toColl)
    )(insertType.getRType), insertType)
    val result = balanceState.map.insert(inserts.map(u => u -> SingleBalance(0L)):_*)
    logger.info(s"Inserting ${inserts.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    updateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue))
  }

  def applyDualContext(updateBox: InputBox, balanceState: BalanceState[DualBalance], inserts: Seq[PartialStateMiner]): InputBox = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(inserts.map(u => u -> DualBalance(0L, 0L)).toArray.map(u => u._1.toColl -> u._2.toColl)
    )(insertType.getRType), insertType)
    val result = balanceState.map.insert(inserts.map(u => u -> DualBalance(0L, 0L)):_*)
    logger.info(s"Inserting ${inserts.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    updateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue))
  }


}




