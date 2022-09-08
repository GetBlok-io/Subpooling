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
class NFTExchangeContract(contract: ErgoContract, shareOp: Address, poolOp: Address,
                          holdingAddress: Address, lpToken: ErgoId, distToken: ErgoId) {
  private val logger: Logger = LoggerFactory.getLogger("HybridExchangeContract")
  def getConstants: Constants = contract.getConstants

  def getErgoScript: String = contract.getErgoScript

  def substConstant(name: String, value: Any): ErgoContract = contract.substConstant(name, value)

  def getErgoTree: Values.ErgoTree = contract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = contract

  /**
   * Perform one emissions cycle, such that next emissions box has correct number of tokens, and next exchange box has required ERG.
   */
  def cycleEmissions(ctx: BlockchainContext, emissionsBox: HybridExchangeBox, ergAfterFees: Long, optLpBoxId: Option[ErgoId] = None): HybridExchangeResults = {
    logger.info(s"LPToken Id: ${lpToken}")
    val lpBox = optLpBoxId.map(o => ctx.getBoxesById(o.toString).head).getOrElse(
      ctx.getUnspentBoxesFor(Address.create(getSwapAddress(ctx.getNetworkType)), 0, 100)
                .asScala.toSeq.filter(_.getTokens.asScala.exists(_.getId == lpToken)).head
    )
    logger.info(s"LP box: ${lpBox.getId}")
    logger.info(s"LP box json: ${lpBox.toJson(true)}")
    val amountToSwap = (ergAfterFees * emissionsBox.proportion.value) / PoolFees.POOL_FEE_CONST
    val amountInErg  = ergAfterFees - amountToSwap
    val outputTokens = calculateMinOutputAmount(amountToSwap, 0.01, lpBox.getValue.toLong,
      lpBox.getTokens.get(2).getValue.toLong, lpBox.getRegisters.get(0).asInstanceOf[ErgoValue[Int]].getValue.toLong,
      1000)

    val adjustedOutput = outputTokens + ((outputTokens * emissionsBox.percentChange.value) / PoolFees.POOL_FEE_CONST)

    val nextEmissions = ctx.newTxBuilder().outBoxBuilder()
      .contract(contract)
      .value(emissionsBox.getValue)
      .registers(emissionsBox.getRegisters:_*)
      .tokens(emissionsBox.getTokens.head, new ErgoToken(emissionsBox.distTokenId, emissionsBox.numTokens - adjustedOutput))
      .build()

    val nextHoldingBox = ctx.newTxBuilder().outBoxBuilder()
      .contract(holdingAddress.toErgoContract)
      .value(amountInErg)
      .tokens(new ErgoToken(distToken, adjustedOutput))
      .build()

    val nextExchange = ctx.newTxBuilder().outBoxBuilder()
      .contract(PK(this.poolOp).contract)
      .value(amountToSwap)
      .build()

    HybridExchangeResults(Seq(nextEmissions, nextHoldingBox, nextExchange), lpBox, adjustedOutput)
  }
}
object NFTExchangeContract {
  val logger: Logger = LoggerFactory.getLogger("NFTExchangeContract")
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

  def generate(ctx: BlockchainContext, shareOperator: Address, poolOperator: Address, holdingContract: ErgoContract,
               lpNFT: ErgoId, distToken: ErgoId, isTest: Boolean = false): NFTExchangeContract = {
    var script = PlasmaScripts.NFT_DEX_SCRIPT

    val constants = new ConstantsBuilder()
      .item("const_shareOpPK", shareOperator.getPublicKey)
      .item("const_poolOpPK", poolOperator.getPublicKey)
      .item("const_LPTokenId", Colls.fromArray(lpNFT.getBytes))
      .item("const_distTokenId", Colls.fromArray(distToken.getBytes))
      .item("const_holdingBytesHashed", Colls.fromArray(Blake2b256.hash(holdingContract.getErgoTree.bytes)))
      .build()

    new NFTExchangeContract(ctx.compileContract(constants, script), shareOperator, poolOperator, holdingContract.toAddress,
      lpNFT, distToken)
  }

  def buildGenesisBox(ctx: BlockchainContext, emissionsContract: NFTExchangeContract, percentChange: Long, poolFee: Long,
                      emissionsToken: ErgoId, distributionToken: ErgoToken): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .contract(emissionsContract.asErgoContract)
      .value(Parameters.MinFee)
      .tokens(new ErgoToken(emissionsToken, 1L), distributionToken)
      .registers(LongReg(percentChange).ergoVal, LongReg(poolFee).ergoVal, LongReg(0L).ergoVal)
      .build()
  }
}









