package io.getblok.subpooling_core
package contracts.emissions

import boxes.HybridExchangeBox
import contracts.emissions.HybridExchangeContract.{HybridExchangeResults, calculateMinOutputAmount, getSwapAddress}
import global.AppParameters
import global.AppParameters.PK
import registers.{LongReg, PoolFees}

import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import scorex.crypto.hash.Blake2b256
import sigmastate.Values
import sigmastate.eval.Colls

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 * Class representing generalized emissions contract that wraps normal ErgoContract
 * @param contract Contract to wrap
 */
class HybridNormalContract(contract: ErgoContract, shareOp: Address, poolOp: Address,
                           holdingAddress: Address, decimals: Long, distToken: ErgoId) {
  private val logger: Logger = LoggerFactory.getLogger("HybridExchangeContract")
  def getConstants: Constants = contract.getConstants

  def getErgoScript: String = contract.getErgoScript

  def substConstant(name: String, value: Any): ErgoContract = contract.substConstant(name, value)

  def getErgoTree: Values.ErgoTree = contract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = contract


}

object HybridNormalContract {
  private val logger: Logger = LoggerFactory.getLogger("HybridNormalContract")


  def simulateSwap(baseAmount: BigInt, exchangeRate: BigInt, decimals: BigInt): BigInt = {
    logger.info(s"Base amount: ${baseAmount}")
    logger.info(s"Decimals: ${decimals}")
    logger.info(s"exchangeRate: ${exchangeRate}")
    (baseAmount * decimals) / exchangeRate
  }

  def generate(ctx: BlockchainContext, shareOperator: Address, poolOperator: Address, holdingContract: ErgoContract,
               distToken: ErgoId, decimals: Long, isTest: Boolean = false): HybridNormalContract = {
    var script = PlasmaScripts.HYBRID_NORM_SCRIPT

    val constants = new ConstantsBuilder()
      .item("const_shareOpPK", shareOperator.getPublicKey)
      .item("const_poolOpPK", poolOperator.getPublicKey)
      .item("const_distTokenId", Colls.fromArray(distToken.getBytes))
      .item("const_holdingBytesHashed", Colls.fromArray(Blake2b256.hash(holdingContract.getErgoTree.bytes)))
      .item("const_decimals", decimals)
      .build()

    new HybridNormalContract(ctx.compileContract(constants, script), shareOperator, poolOperator, holdingContract.toAddress,
      decimals, distToken)
  }

  def buildGenesisBox(ctx: BlockchainContext, emissionsContract: HybridNormalContract, poolFee: Long,
                      proportion: Long, currentHeight: Long, emissionsToken: ErgoId, distributionToken: ErgoToken): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .contract(emissionsContract.asErgoContract)
      .value(Parameters.MinFee)
      .tokens(new ErgoToken(emissionsToken, 1L), distributionToken)
      .registers(LongReg(poolFee).ergoVal, LongReg(proportion).ergoVal, LongReg(currentHeight + (720 * 10)).ergoVal)
      .build()
  }
}









