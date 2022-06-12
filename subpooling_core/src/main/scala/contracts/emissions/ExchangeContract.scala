package io.getblok.subpooling_core
package contracts.emissions

import boxes.{EmissionsBox, ExchangeEmissionsBox}
import global.AppParameters
import global.AppParameters.PK
import registers.{LongReg, PoolFees, PropBytes}

import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.emissions.EmissionsContract.logger
import io.getblok.subpooling_core.contracts.emissions.ExchangeContract.{ExchangeCycleResults, MAINNET_SWAP_ADDRESS, getSwapAddress, simulateSwap}
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
class ExchangeContract(val contract: ErgoContract, shareOp: Address, poolOp: Address,
                       holdingAddress: Address, lpToken: ErgoId, distToken: ErgoId) {
  private val logger: Logger = LoggerFactory.getLogger("ExchangeContract")
  def getConstants: Constants = contract.getConstants

  def getErgoScript: String = contract.getErgoScript

  def substConstant(name: String, value: Any): ErgoContract = contract.substConstant(name, value)

  def getErgoTree: Values.ErgoTree = contract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = contract

  /**
   * Perform one emissions cycle, such that next emissions box has correct number of tokens, and next exchange box has required ERG.
   */
  def cycleEmissions(ctx: BlockchainContext, emissionsBox: ExchangeEmissionsBox, ergAfterFees: Long, optLpBoxId: Option[ErgoId] = None): ExchangeCycleResults = {
    logger.info(s"LPToken Id: ${lpToken}")
    val lpBox = optLpBoxId.map(o => ctx.getBoxesById(o.toString).head).getOrElse(
      ctx.getUnspentBoxesFor(Address.create(getSwapAddress(ctx.getNetworkType)), 0, 100)
                .asScala.toSeq.filter(_.getTokens.asScala.exists(_.getId == lpToken)).head
    )
    logger.info(s"LP box: ${lpBox.getId}")
    logger.info(s"LP box json: ${lpBox.toJson(true)}")
    val outputTokens = simulateSwap(ergAfterFees, 0.01, lpBox.getValue.toLong,
      lpBox.getTokens.get(2).getValue.toLong, lpBox.getRegisters.get(0).asInstanceOf[ErgoValue[Int]].getValue.toLong,
      1000)

    val adjustedOutput = outputTokens + ((outputTokens * emissionsBox.percentChange.value) / PoolFees.POOL_FEE_CONST)

    val nextEmissions = ctx.newTxBuilder().outBoxBuilder()
      .contract(contract)
      .value(emissionsBox.getValue)
      .registers(emissionsBox.getRegisters:_*)
      .tokens(emissionsBox.getTokens.head, new ErgoToken(emissionsBox.distTokenId, emissionsBox.numTokens - adjustedOutput))
      .build()

    val nextExchange = ctx.newTxBuilder().outBoxBuilder()
      .contract(PK(this.poolOp).contract)
      .value(ergAfterFees)
      .build()

    ExchangeCycleResults(Seq(nextEmissions, nextExchange), lpBox, adjustedOutput)
  }
}

object ExchangeContract {
  val logger: Logger = LoggerFactory.getLogger("ExchangeEmissionsContract")
  case class ExchangeCycleResults(outputs: Seq[OutBox], lpBox: InputBox, tokensForHolding: Long)
  final val MAINNET_SWAP_ADDRESS =
    "5vSUZRZbdVbnk4sJWjg2uhL94VZWRg4iatK9VgMChufzUgdihgvhR8yWSUEJKszzV7Vmi6K8hCyKTNhUaiP8p5ko6YEU9yfHpjVuXdQ4i5p4cRCzch6ZiqWrNukYjv7Vs5jvBwqg5hcEJ8u1eerr537YLWUoxxi1M4vQxuaCihzPKMt8NDXP4WcbN6mfNxxLZeGBvsHVvVmina5THaECosCWozKJFBnscjhpr3AJsdaL8evXAvPfEjGhVMoTKXAb2ZGGRmR8g1eZshaHmgTg2imSiaoXU5eiF3HvBnDuawaCtt674ikZ3oZdekqswcVPGMwqqUKVsGY4QuFeQoGwRkMqEYTdV2UDMMsfrjrBYQYKUBFMwsQGMNBL1VoY78aotXzdeqJCBVKbQdD3ZZWvukhSe4xrz8tcF3PoxpysDLt89boMqZJtGEHTV9UBTBEac6sDyQP693qT3nKaErN8TCXrJBUmHPqKozAg9bwxTqMYkpmb9iVKLSoJxG7MjAj72SRbcqQfNCVTztSwN3cRxSrVtz4p87jNFbVtFzhPg7UqDwNFTaasySCqM"
  final val TESTNET_SWAP_ADDRESS =
    "2HQupJD5fRzN39YwSHoxzgW2DojmcPtfjDL71yB15arP"

  def getSwapAddress(networkType: NetworkType) = {
    networkType match {
      case NetworkType.MAINNET =>
        MAINNET_SWAP_ADDRESS
      case NetworkType.TESTNET =>
        TESTNET_SWAP_ADDRESS
    }
  }
  def simulateSwap(baseAmount: Long, maxSlippagePercentage: Double, xAssetAmount: Long, yAssetAmount: Long, feeNumerator: Long, feeDenominator: Long): Long = {
    val outputNum = ((yAssetAmount / 100000) * ((baseAmount * feeNumerator) / 100000))
    logger.info(s"OutputNumerator: ${outputNum}")
    val outputDenom = (((xAssetAmount + ((xAssetAmount * 1) / 10000)) * feeDenominator) + (baseAmount * feeNumerator)) / 100000
    logger.info(s"OutputDenominator: ${outputDenom}")
    val unadjustedOutput = outputNum / outputDenom
    logger.info(s"unAdj Output: ${unadjustedOutput}")
    val reAdjOutput = unadjustedOutput * 100000
    logger.info(s"Output reAdj: ${reAdjOutput}")
    val outputAmountLong: Long = reAdjOutput.toLong
    outputAmountLong
  }


  def generate(ctx: BlockchainContext, shareOperator: Address, poolOperator: Address, holdingContract: HoldingContract,
               lpToken: ErgoId, distToken: ErgoId, isTest: Boolean = false): ExchangeContract = {
    var script = Scripts.EX_EMISSIONS_SCRIPT
    if(isTest)
      script = Scripts.EX_EMISSIONS_TEST_SCRIPT
    val constants = new ConstantsBuilder()
      .item("const_shareOpPK", shareOperator.getPublicKey)
      .item("const_poolOpPK", poolOperator.getPublicKey)
      .item("const_LPTokenId", Colls.fromArray(lpToken.getBytes))
      .item("const_distTokenId", Colls.fromArray(distToken.getBytes))
      .item("const_holdingBytesHashed", Colls.fromArray(Blake2b256.hash(holdingContract.getErgoTree.bytes)))
      .build()

    new ExchangeContract(ctx.compileContract(constants, script), shareOperator, poolOperator, holdingContract.toAddress,
      lpToken, distToken)
  }

  def buildGenesisBox(ctx: BlockchainContext, emissionsContract: ExchangeContract, percentChange: Long, poolFee: Long,
                      emissionsToken: ErgoId, distributionToken: ErgoToken): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .contract(emissionsContract.contract)
      .value(Parameters.MinFee)
      .tokens(new ErgoToken(emissionsToken, 1L), distributionToken)
      .registers(LongReg(percentChange).ergoVal, LongReg(poolFee).ergoVal)
      .build()
  }
}





