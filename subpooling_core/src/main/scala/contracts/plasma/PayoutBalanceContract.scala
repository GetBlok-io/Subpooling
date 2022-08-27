package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract.logger
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, DualBalance, PartialStateMiner, SingleBalance, StateBalance, StateMiner}
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoToken, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values
import sigmastate.eval.Colls

import scala.collection.immutable


case class PayoutBalanceContract(contract: ErgoContract) {
  import PayoutBalanceContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = "script"
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}
object PayoutBalanceContract {

  def routeScript(scriptType: ScriptType): String = {
    scriptType match {
      case PlasmaScripts.SINGLE => PlasmaScripts.SINGLE_PAYOUT_SCRIPT
      case PlasmaScripts.DUAL => PlasmaScripts.HYBRID_PAYOUT_SCRIPT
    }
  }

  private val logger: Logger = LoggerFactory.getLogger("PayoutBalanceContract")

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

  def applyContext[T <: StateBalance](stateBox: InputBox, balanceState: BalanceState[T], payouts: Seq[StateMiner], zero: T): (InputBox, immutable.IndexedSeq[(StateMiner, T)]) = {
    val insertType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))

    val distinctPayouts = payouts.foldLeft(Seq[StateMiner]()){
      (z, b) =>
        if(!z.exists(p => p.address.toString == b.address.toString)){
          z ++ Seq(b)
        }else{
          z
        }
    }
    val lastBalances = balanceState.map.lookUp((distinctPayouts.map(_.toPartialStateMiner)):_*).response.map(_.tryOp.get)

    val lastBalanceMap = distinctPayouts.indices.map(i => distinctPayouts(i) -> lastBalances(i).get)
    val nextBalanceMap = distinctPayouts.map(u => u.toPartialStateMiner -> zero)

    val distinctBalanceMap = nextBalanceMap.foldLeft(Seq[(PartialStateMiner, T)]()){
      (z, b) =>
        if(!z.exists(p => p._1.toString == b._1.toString)){
          z ++ Seq(b)
        }else{
          z
        }
    }

    val updateErgoVal = ErgoValue.of(Colls.fromArray(distinctBalanceMap.map(u => u._1.toColl -> u._2.toColl).toArray
    )(insertType.getRType), insertType)

    val result = balanceState.map.update(distinctBalanceMap:_*)
    logger.info(s"${distinctBalanceMap.head.toString()}")
    logger.info(s"Updating ${distinctBalanceMap.length} balance states")
    logger.info(s"Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    stateBox.withContextVars(ContextVar.of(0.toByte, updateErgoVal), ContextVar.of(1.toByte, result.proof.ergoValue)) -> lastBalanceMap
  }


  def buildERGPaymentBoxes(ctx: BlockchainContext, payouts: Seq[(StateMiner, SingleBalance)]): Seq[OutBox] = {
    logger.info("Building payout boxes: ")
    val paymentBoxes = {
      for(u <- payouts) yield {
        logger.info(s"${u._1.toString}: ${u._2}")
        ctx.newTxBuilder().outBoxBuilder()
          .value( u._2.balance )
          .contract(u._1.address.toErgoContract)
          .build()
      }
    }
    paymentBoxes
  }

  def buildHybridPaymentBoxes(ctx: BlockchainContext, tokenId: ErgoId, payouts: Seq[(StateMiner, DualBalance)]): Seq[OutBox] = {
    logger.info("Building payout boxes: ")
    val paymentBoxes = {
      for(u <- payouts) yield {
        logger.info(s"${u._1.toString}: ${u._2}")
        ctx.newTxBuilder().outBoxBuilder()
          .value( u._2.balance )
          .tokens( new ErgoToken(tokenId, u._2.balanceTwo) )
          .contract(u._1.address.toErgoContract)
          .build()
      }
    }
    paymentBoxes
  }
}





