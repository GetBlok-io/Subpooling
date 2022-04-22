package io.getblok.subpooling_core
package contracts.emissions

import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import global.AppParameters
import transactions.DistributionTx

import io.getblok.subpooling_core.boxes.EmissionsBox
import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.holding.HoldingContract
import io.getblok.subpooling_core.global.AppParameters.PK
import io.getblok.subpooling_core.registers.{LongReg, PropBytes}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.{Logger, LoggerFactory}
import scorex.crypto.hash.Blake2b256
import sigmastate.Values
import sigmastate.eval.Colls

/**
 * Class representing generalized emissions contract that wraps normal ErgoContract
 * @param contract Contract to wrap
 */
class EmissionsContract(val contract: ErgoContract) {

  def getConstants: Constants = contract.getConstants

  def getErgoScript: String = contract.getErgoScript

  def substConstant(name: String, value: Any): ErgoContract = contract.substConstant(name, value)

  def getErgoTree: Values.ErgoTree = contract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = contract

  /**
   * Perform one emissions cycle, such that next emissions box has correct number of tokens, and next exchange box has required ERG
   */
  def cycleEmissions(ctx: BlockchainContext, emissionsBox: EmissionsBox, ergAfterFees: Long): Seq[OutBox] = {
    val nextEmissions = ctx.newTxBuilder().outBoxBuilder()
      .contract(contract)
      .value(emissionsBox.getValue)
      .registers(emissionsBox.getRegisters:_*)
      .tokens(emissionsBox.getTokens.head, new ErgoToken(emissionsBox.tokenId, emissionsBox.numTokens - emissionsBox.emissionReward.value))
      .build()

    val nextExchange = ctx.newTxBuilder().outBoxBuilder()
      .contract(PK(emissionsBox.ergHolder.address).contract)
      .value(ergAfterFees)
      .build()

    Seq(nextEmissions, nextExchange)

  }
  /**
   * Modify emissions box by changing reward and adding new exchange address
   * */
  def modifyEmissions(ctx: BlockchainContext, emissionsBox: EmissionsBox, newReward: Long, newExchange: Address): OutBox = {
    val nextEmissions = ctx.newTxBuilder().outBoxBuilder()
      .contract(contract)
      .value(emissionsBox.getValue)
      .registers(LongReg(newReward).ergoVal, PropBytes.ofAddress(newExchange)(ctx.getNetworkType).ergoVal)
      .tokens(emissionsBox.getTokens:_*)
      .build()
    nextEmissions
  }
}

object EmissionsContract {
  private val logger: Logger = LoggerFactory.getLogger("EmissionsContract")

  def generate(ctx: BlockchainContext, shareOperator: Address, poolOperator: Address, holdingContract: HoldingContract, isTest: Boolean = false): EmissionsContract = {
    var script = Scripts.SIMPLE_EMISSIONS_SCRIPT

    if(isTest)
      script = Scripts.EMISSIONS_TEST_SCRIPT
    val constants = new ConstantsBuilder()
      .item("const_shareOpPK", shareOperator.getPublicKey)
      .item("const_poolOpPK", poolOperator.getPublicKey)
      .item("const_holdingBytesHashed", Colls.fromArray(Blake2b256.hash(holdingContract.getErgoTree.bytes)))
      .build()

    logger.info(s"Holding Contract bytes: ${holdingContract.getErgoTree.bytes.length}")
    logger.info(s"Hashed bytes: ${Blake2b256.hash(holdingContract.getErgoTree.bytes).length}")

    new EmissionsContract(ctx.compileContract(constants, script))
  }

  def buildGenesisBox(ctx: BlockchainContext, emissionsContract: EmissionsContract, blockReward: Long, exchangeAddress: Address,
                      emissionsToken: ErgoId, distributionToken: ErgoToken): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .contract(emissionsContract.contract)
      .value(Parameters.MinFee)
      .tokens(new ErgoToken(emissionsToken, 1L), distributionToken)
      .registers(LongReg(blockReward).ergoVal, PropBytes.ofAddress(exchangeAddress)(ctx.getNetworkType).ergoVal)
      .build()
  }
}



