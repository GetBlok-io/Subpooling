package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, PartialStateMiner, SingleBalance, StateBalance, StateMiner}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values
import sigmastate.eval.Colls


case class InsertBalanceContract(contract: ErgoContract) {
  import InsertBalanceContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = "script"
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}

object InsertBalanceContract {

  private val logger: Logger = LoggerFactory.getLogger("InsertBalanceContract")

  def routeScript(scriptType: ScriptType): String = {
    scriptType match {
      case PlasmaScripts.SINGLE => PlasmaScripts.SINGLE_INSERT_SCRIPT
      case PlasmaScripts.DUAL => PlasmaScripts.HYBRID_INSERT_SCRIPT
    }
  }

  def generate(ctx: BlockchainContext, poolNFT: ErgoId, scriptType: ScriptType): ErgoContract = {
    val constants = ConstantsBuilder.create().item("const_poolNFT", Colls.fromArray(poolNFT.getBytes)).build()
    val contract: ErgoContract = ctx.compileContract(constants, routeScript(scriptType))
    contract
  }

  def buildBox(ctx: BlockchainContext, poolNFT: ErgoId, scriptType: ScriptType, optValue: Option[Long] = None): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .contract(generate(ctx, poolNFT, scriptType))
      .build()
  }

  def applyContext[T <: StateBalance](updateBox: InputBox, balanceState: BalanceState[T], inserts: Seq[PartialStateMiner],
                                     zero: T): InputBox = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(inserts.map(u => u -> zero).toArray.map(u => u._1.toColl -> u._2.toColl)
    )(insertType.getRType), insertType)
    val result = balanceState.map.insert(inserts.map(u => u -> zero):_*)
    logger.info(s"Inserting ${inserts.length} share states")
    logger.info(s"Sample: ${updateErgoVal.getValue.toArray.head}")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    updateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue))
  }


}




