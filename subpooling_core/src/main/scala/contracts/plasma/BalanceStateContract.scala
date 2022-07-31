package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.getblok_plasma.collections.Proof
import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, PartialStateMiner, ShareState, SingleBalance, StateMiner, StateScore}
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{Address, BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoToken, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import scorex.crypto.authds.avltree.batch.Insert
import scorex.crypto.hash.Blake2b256
import sigmastate.Values
import sigmastate.eval.Colls
import special.collection.Coll


case class BalanceStateContract(contract: ErgoContract){
  import BalanceStateContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = script
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}

object BalanceStateContract {

  val script: String = PlasmaScripts.BALANCE_STATE_SCRIPT
  private val logger: Logger = LoggerFactory.getLogger("BalanceStateContract")

  def generate(ctx: BlockchainContext, poolOp: Address, poolTag: ErgoId, scriptType: ScriptType): ErgoContract = {
    val commands = {
      Seq(
        InsertBalanceContract.generate(ctx, poolTag, scriptType),
        UpdateBalanceContract.generate(ctx, poolTag, scriptType),
        PayoutBalanceContract.generate(ctx, poolTag, scriptType),
        DeleteBalanceContract.generate(ctx, poolTag, scriptType)
      )
    }
    val commandBytes = commands.map(c => Colls.fromArray(Blake2b256(c.getErgoTree.bytes)).asInstanceOf[Coll[java.lang.Byte]])
    val commandColl = Colls.fromArray(commandBytes.toArray)
    val constants = ConstantsBuilder.create()
      .item("const_poolOpPK", poolOp.getPublicKey)
      .item("const_commandBytes", commandColl)
      .build()
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  def buildBox(ctx: BlockchainContext, balanceState: BalanceState[_], poolTag: ErgoId, poolOp: Address,
               scriptType: ScriptType, optValue: Option[Long] = None, optToken: Option[ErgoToken] = None): OutBox = {

    var tokenSeq = Seq(new ErgoToken(poolTag, 1L))
    if(optToken.isDefined)
      tokenSeq = tokenSeq ++ Seq(optToken.get)
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .registers(balanceState.map.ergoValue)
      .contract(generate(ctx, poolOp, poolTag, scriptType))
      .tokens(tokenSeq: _*)
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

  def buildPaymentBoxes(ctx: BlockchainContext, payouts: Seq[(StateMiner, SingleBalance)]): Seq[OutBox] = {
    for(u <- payouts) yield {
      logger.info(s"Balance: ${u._2}")
      ctx.newTxBuilder().outBoxBuilder()
        .value( u._2.balance )
        .contract(u._1.address.toErgoContract)
        .build()
    }
  }
}



