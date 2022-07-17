package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.getblok_plasma.collections.Proof
import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, PartialStateMiner, ShareState, StateBalance, StateMiner, StateScore}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoToken, ErgoType, ErgoValue, InputBox, OutBox}
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

  def buildStateBox(ctx: BlockchainContext, balanceState: BalanceState, poolTag: ErgoId ,optValue: Option[Long] = None): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .registers(balanceState.map.ergoValue)
      .contract(generateStateContract(ctx))
      .tokens(new ErgoToken(poolTag, 1L))
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



