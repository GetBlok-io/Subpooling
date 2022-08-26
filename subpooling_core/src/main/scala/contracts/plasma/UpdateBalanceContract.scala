package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract.{logger, script}
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, PartialStateMiner, SingleBalance}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoToken, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values
import sigmastate.eval.Colls


case class UpdateBalanceContract(contract: ErgoContract) {
  import UpdateBalanceContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = "script"
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}
object UpdateBalanceContract {

  private val logger: Logger = LoggerFactory.getLogger("UpdateBalanceContract")

  def routeScript(scriptType: ScriptType): String = {
    scriptType match {
      case PlasmaScripts.SINGLE => PlasmaScripts.SINGLE_UPDATE_SCRIPT
      case PlasmaScripts.DUAL => PlasmaScripts.HYBRID_UPDATE_SCRIPT
    }
  }

  def generate(ctx: BlockchainContext, poolNFT: ErgoId, scriptType: ScriptType): ErgoContract = {
    val constants = ConstantsBuilder.create().item("const_poolNFT", Colls.fromArray(poolNFT.getBytes)).build()
    val contract: ErgoContract = ctx.compileContract(constants, routeScript(scriptType))
    contract
  }

  def buildBox(ctx: BlockchainContext, poolNFT: ErgoId, scriptType: ScriptType, optValue: Option[Long] = None, optToken: Option[ErgoToken] = None): OutBox = {
    val unbuiltBox = ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .contract(generate(ctx, poolNFT, scriptType))

    if(optToken.isDefined){
      unbuiltBox
        .tokens(optToken.get)
        .build()
    }else{
      unbuiltBox.build()
    }

  }

  def applySingleContext(stateBox: InputBox, balanceState: BalanceState[SingleBalance], balanceChanges: Seq[(PartialStateMiner, SingleBalance)]): (InputBox, Long) = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))

    val distinctChanges = balanceChanges.foldLeft(Seq[(PartialStateMiner, SingleBalance)]()){
      (z, b) =>
        if(!z.exists(p => p._1.toString == b._1.toString)){
          z ++ Seq(b)
        }else{
          z
        }
    }

    val updateErgoVal = ErgoValue.of(Colls.fromArray(distinctChanges.map(u => u._1.toColl -> u._2.toColl).toArray
    )(insertType.getRType), insertType)

    val oldBalances = balanceState.map.lookUp(distinctChanges.map(_._1):_*).response

    val updates = distinctChanges.indices.map{
      idx =>
        val keyChange = distinctChanges(idx)
        val oldBalance = oldBalances(idx).tryOp.get.get

        (keyChange._1 -> keyChange._2.copy(balance = keyChange._2.balance + oldBalance.balance))
    }

    val result = balanceState.map.update(updates:_*)
    logger.info(s"${distinctChanges.head.toString()}")
    logger.info(s"Updating ${distinctChanges.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue)) -> distinctChanges.map(_._2.balance).sum
  }

  def applyDualContext(stateBox: InputBox, balanceState: BalanceState[DualBalance], balanceChanges: Seq[(PartialStateMiner, DualBalance)]): (InputBox, (Long, Long)) = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))

    val distinctChanges = balanceChanges.foldLeft(Seq[(PartialStateMiner, DualBalance)]()){
      (z, b) =>
        if(!z.exists(p => p._1.toString == b._1.toString)){
          z ++ Seq(b)
        }else{
          z
        }
    }

    val updateErgoVal = ErgoValue.of(Colls.fromArray(distinctChanges.map(u => u._1.toColl -> u._2.toColl).toArray
    )(insertType.getRType), insertType)

    val oldBalances = balanceState.map.lookUp(distinctChanges.map(_._1):_*).response

    val updates = distinctChanges.indices.map{
      idx =>
        val keyChange = distinctChanges(idx)
        val oldBalance = oldBalances(idx).tryOp.get.get

        (keyChange._1 -> keyChange._2.copy(
          balance = keyChange._2.balance + oldBalance.balance,
          balanceTwo = keyChange._2.balanceTwo + oldBalance.balanceTwo
        ))
    }

    val result = balanceState.map.update(updates:_*)
    logger.info(s"${distinctChanges.head.toString()}")
    logger.info(s"Updating ${distinctChanges.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal),
      ContextVar.of(1.toByte, result.proof.ergoValue)) -> (distinctChanges.map(_._2.balance).sum, distinctChanges.map(_._2.balanceTwo).sum)
  }

}





