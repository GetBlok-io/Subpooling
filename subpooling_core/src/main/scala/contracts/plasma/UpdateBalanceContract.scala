package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract.logger
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, PartialStateMiner, StateBalance}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values
import sigmastate.eval.Colls


case class UpdateBalanceContract(contract: ErgoContract) {
  import UpdateBalanceContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = script
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}
object UpdateBalanceContract {
  val script: String = Scripts.UPDATE_BALANCE_SCRIPT
  private val logger: Logger = LoggerFactory.getLogger("UpdateBalanceContract")

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

  def applyContext(stateBox: InputBox, balanceState: BalanceState, balanceChanges: Seq[(PartialStateMiner, StateBalance)]): (InputBox, Long) = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateErgoVal = ErgoValue.of(Colls.fromArray(balanceChanges.map(u => u._1.toColl -> u._2.toColl).toArray
    )(insertType.getRType), insertType)

    val oldBalances = balanceState.map.lookUp(balanceChanges.map(_._1):_*).response

    val updates = balanceChanges.indices.map{
      idx =>
        val keyChange = balanceChanges(idx)
        val oldBalance = oldBalances(idx).tryOp.get.get

        (keyChange._1 -> keyChange._2.copy(balance = keyChange._2.balance + oldBalance.balance))
    }

    val result = balanceState.map.update(updates:_*)
    logger.info(s"${balanceChanges.head.toString()}")
    logger.info(s"Updating ${balanceChanges.length} share states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue)) -> balanceChanges.map(_._2.balance).sum
  }

}





