package io.getblok.subpooling_core
package contracts.emissions

import boxes.{ExchangeEmissionsBox, ProportionalEmissionsBox}
import contracts.emissions.ExchangeContract.{ExchangeCycleResults, getSwapAddress, simulateSwap}
import global.AppParameters
import global.AppParameters.PK
import registers.{LongReg, PoolFees}

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.emissions.ProportionalEmissionsContract.ProportionCycleResults
import io.getblok.subpooling_core.contracts.holding.HoldingContract
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import scorex.crypto.hash.Blake2b256
import sigmastate.Values
import sigmastate.eval.Colls

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

/**
 * Class representing generalized emissions contract that wraps normal ErgoContract
 * @param contract Contract to wrap
 */
class ProportionalEmissionsContract(val contract: ErgoContract, shareOp: Address, poolOp: Address,
                                    holdingAddress: Address, distToken: ErgoId, decimalPlaces: Long) {
  private val logger: Logger = LoggerFactory.getLogger("ProportionalEmissionsContract")
  def getConstants: Constants = contract.getConstants

  def getErgoScript: String = contract.getErgoScript

  def substConstant(name: String, value: Any): ErgoContract = contract.substConstant(name, value)

  def getErgoTree: Values.ErgoTree = contract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = contract

  /**
   * Perform one emissions cycle, such that next emissions box has correct number of tokens, and next exchange box has required ERG.
   */
  def cycleEmissions(ctx: BlockchainContext, emissionsBox: ProportionalEmissionsBox, ergAfterFees: Long): ProportionCycleResults = {
    val outputTokens = (ergAfterFees * emissionsBox.proportion.value) / decimalPlaces
    val nextEmissions = ctx.newTxBuilder().outBoxBuilder()
      .contract(contract)
      .value(emissionsBox.getValue)
      .registers(emissionsBox.getRegisters:_*)
      .tokens(emissionsBox.getTokens.head, new ErgoToken(emissionsBox.distTokenId, emissionsBox.numTokens - outputTokens))
      .build()

    ProportionCycleResults(Seq(nextEmissions), outputTokens)
  }
}
object ProportionalEmissionsContract {
  val logger: Logger = LoggerFactory.getLogger("ProportionalEmissionsContract")
  case class ProportionCycleResults(outputs: Seq[OutBox], tokensForHolding: Long)

  def generate(ctx: BlockchainContext, shareOperator: Address, poolOperator: Address, holdingContract: HoldingContract,
               distToken: ErgoId, decimalPlaces: Long, isTest: Boolean = false): ProportionalEmissionsContract = {
    var script = Scripts.PROP_EMISSIONS_SCRIPT
    if(isTest)
      script = Scripts.PROP_EMISSIONS_TEST_SCRIPT
    val constants = new ConstantsBuilder()
      .item("const_shareOpPK", shareOperator.getPublicKey)
      .item("const_poolOpPK", poolOperator.getPublicKey)
      .item("const_distTokenId", Colls.fromArray(distToken.getBytes))
      .item("const_holdingBytesHashed", Colls.fromArray(Blake2b256.hash(holdingContract.getErgoTree.bytes)))
      .item("const_decimalPlaces", decimalPlaces)
      .build()

    new ProportionalEmissionsContract(ctx.compileContract(constants, script), shareOperator, poolOperator, holdingContract.toAddress,
      distToken, decimalPlaces)
  }

  def buildGenesisBox(ctx: BlockchainContext, emissionsContract: ProportionalEmissionsContract, proportion: Long, poolFee: Long,
                      emissionsToken: ErgoId, distributionToken: ErgoToken): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .contract(emissionsContract.contract)
      .value(Parameters.MinFee)
      .tokens(new ErgoToken(emissionsToken, 1L), distributionToken)
      .registers(LongReg(proportion).ergoVal, LongReg(poolFee).ergoVal)
      .build()
  }
}







