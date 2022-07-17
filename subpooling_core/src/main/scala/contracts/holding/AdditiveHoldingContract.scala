package io.getblok.subpooling_core
package contracts.holding

import boxes.BoxHelpers
import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder, HoldingSetBuilder}
import global.AppParameters
import logging.LoggingHandler
import registers.{PoolFees, PropBytes, ShareDistribution}
import transactions.{CreateCommandTx, DistributionTx}

import io.getblok.subpooling_core.contracts.Models.Scripts
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.eval.Colls
// TODO: Clean up offchain code to look nicer and possibly be more efficient

/**
 * This is a simple holding contract that distributes PPS and saves minimum payouts that are then applied to next
 * command box output. Then it requires that all outputs have some token attatched to them.
 * @param holdingContract ErgoContract to build AdditiveHoldingContract from.
 */
class AdditiveHoldingContract(holdingContract: ErgoContract) extends HoldingContract(holdingContract) {
  val logger: Logger = LoggerFactory.getLogger("AdditiveHoldingContract")
  final val MIN_PAYMENT_THRESHOLD = Parameters.OneErg / 10L // TODO: Make this an AppParameter


  override def applyToCommand(commandTx: CreateCommandTx): CommandOutputBuilder = {
    val metadataBox = commandTx.metadataInputBox
    val storedPayouts = metadataBox.shareDistribution.dist.map(d => d._2.getStored).sum

    val holdingBoxes = commandTx.holdingInputs

    val currentDistribution = commandTx.cOB.metadataRegisters.shareDist
    val lastDistribution = metadataBox.shareDistribution

    val holdingBoxValues = holdingBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
        accum + box.getValue
    }

    val currentPoolFees = metadataBox.getPoolFees
    val currentTxFee = Parameters.MinFee * currentDistribution.size

    val totalOwedPayouts =
      lastDistribution.filter(c => c._2.getStored < c._2.getMinPay).dist.map(c => c._2.getStored).sum

    val totalRewards = holdingBoxValues - totalOwedPayouts
    val feeList = currentPoolFees.fees.map{
      // Pool fee is defined as x/100000 of total inputs value.
      poolFee =>
        val feeAmount: Long = (poolFee._2.toLong * totalRewards)/PoolFees.POOL_FEE_CONST.toLong
        val feeNoDust: Long = BoxHelpers.removeDust(feeAmount)
        (poolFee._1 , feeNoDust)
    }
    // Total amount in holding after pool fees and tx fees.
    // This is the total amount of ERG to be distributed to pool members
    val accumFees = (feeList.toArray.foldLeft(totalRewards){
      (accum, poolFeeVal) => accum - poolFeeVal._2
    })

    val totalValAfterFees = accumFees - currentTxFee
    var updatedConsensus = currentDistribution.dist
    val totalShares = updatedConsensus.map(d => d._2.getScore).sum
    val tokenRate = BigDecimal(holdingBoxes.head.getTokens.get(0).getValue) / holdingBoxes.head.getValue
    logger.info(s"Current token rate, (tokens per nanoErg): ${tokenRate}")
    var shareScoreLeft = 0L
    updatedConsensus = updatedConsensus.map{
      consVal =>
        val shareNum = consVal._2.getScore
        var currentMinPayout = consVal._2.getMinPay
        logger.info(s"Share score for ${consVal._1.address}: $shareNum")
        var valueFromShares = ((totalValAfterFees * BigDecimal(shareNum)) / BigDecimal(totalShares)).toLong
        valueFromShares = BoxHelpers.removeDust(valueFromShares)

        logger.info("Member: " + consVal._1.address)
        logger.info("Value from shares: " + valueFromShares)

        logger.info("Current Min Payout: " + currentMinPayout)

        val owedPayment =
          if(lastDistribution.dist.exists(sc => consVal._1 == sc._1)){
            val lastConsValues = lastDistribution.filter(sc => consVal._1 == sc._1 ).head._2
            val lastStoredPayout = lastConsValues.getStored
//            println("Last Stored Payout: " + lastStoredPayout)
            if(lastStoredPayout + valueFromShares >= currentMinPayout)
              0L
            else{
              lastStoredPayout + valueFromShares
            }
          }else{
            if(valueFromShares >= currentMinPayout)
              0L
            else{
              valueFromShares
            }
          }
        logger.info(s"Owed Payment: $owedPayment")
        val newConsensusInfo = consVal._2.withStored(owedPayment)
        (consVal._1, newConsensusInfo)
    }
    val newShareDistribution = new ShareDistribution(updatedConsensus)
    val newMetadataRegisters = commandTx.cOB.metadataRegisters.copy(shareDist = newShareDistribution)

    commandTx.cOB.setMetadata(newMetadataRegisters)
  }

  /**
   * Generates a HoldingOutputBuilder that follows consensus.
   * @param ctx Blockchain context
   * @return Returns HoldingOutputBuilder to use in transaction
   */
  override def generateInitialOutputs(ctx: BlockchainContext, distributionTx: DistributionTx, holdingBoxes: List[InputBox]): HoldingOutputBuilder = {
    implicit val networkType: NetworkType = AppParameters.networkType
    logger.info("Now generating initial holding outputs for AdditiveHoldingContract")
    val metadataBox = distributionTx.metadataInputBox
    val commandBox = distributionTx.commandInputBox
    val holdingAddress = distributionTx.holdingContract.toAddress
    val initBoxes: List[InputBox] = List(metadataBox.asInput, commandBox.asInput)
    val inputList = initBoxes++holdingBoxes
    val inputBoxes: Array[InputBox] = inputList.toArray

    val feeAddresses = metadataBox.getPoolFees.fees.map(c => c._1.address)

    val holdingBytes = PropBytes.ofAddress(holdingAddress)
    val TOTAL_HOLDED_VALUE = inputBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
        val boxPropBytes = PropBytes.ofErgoTree(box.getErgoTree)
        if(boxPropBytes == holdingBytes){
          accum + box.getValue
        }else
          accum
    }
    logger.info("Total Value Held: " + TOTAL_HOLDED_VALUE)
    val tokenRate = BigDecimal(holdingBoxes.head.getTokens.get(0).getValue) / holdingBoxes.head.getValue
    logger.info(s"Current token rate, (tokens per nanoErg): ${tokenRate}")
    val lastConsensus = metadataBox.shareDistribution
    val currentConsensus = commandBox.shareDistribution
    val currentPoolFees = metadataBox.poolFees
    val currentTxFee = Parameters.MinFee * currentConsensus.size

    val totalOwedPayouts =
      lastConsensus.filter(c => c._2.getStored < c._2.getMinPay).dist.map(c => c._2.getStored).sum
    val totalRewards = TOTAL_HOLDED_VALUE - totalOwedPayouts

    val feeList = currentPoolFees.fees.map{
      f =>
        val feeAmount = (f._2.toLong * totalRewards) / PoolFees.POOL_FEE_CONST.toLong
        val dustRemoved = BoxHelpers.removeDust(feeAmount)
        (f._1, dustRemoved)
    }

    // Total amount in holding after pool fees and tx fees.
    // This is the total amount of ERG to be distributed to pool members
    val accumFees = (feeList.toArray.foldLeft(totalRewards){
      (accum, poolFeeVal) => accum - poolFeeVal._2
    })

    val totalValAfterFees = accumFees - currentTxFee
    logger.info(s"Total Value After Fees: $totalValAfterFees")
    val totalShares = currentConsensus.dist.map(c => c._2.getScore).sum

    // Returns some value that is a percentage of the total rewards after the fees.
    // The percentage used is the proportion of the share number passed in over the total number of shares.
    def getValueFromShare(shareNum: Long) = {
      if(totalShares != 0) {
        val newBoxValue = ((totalValAfterFees * BigDecimal(shareNum)) / BigDecimal(totalShares)).toLong
        val dustRemoved = BoxHelpers.removeDust(newBoxValue)
        dustRemoved
      }else
        0L
    }
    val totalTokens = holdingBoxes.map(_.getTokens.get(0).getValue.toLong).sum

    // Maps each propositionBytes stored in the consensus to a value obtained from the shares.
    val boxValueMap = currentConsensus.dist.map{
      consVal =>

        val shareNum = consVal._2.getScore
        val currentMinPayout = consVal._2.getMinPay
        val valueFromShares = getValueFromShare(shareNum)
        logger.info("Current member in boxValueMap")
        logger.info(consVal._1.address.toString + s": ${consVal._2}")
        logger.info(s"Value From Shares: $valueFromShares")
        //println("Value From Shares: " + valueFromShares)
        if(lastConsensus.dist.exists(sc => consVal._1 == sc._1)){
          val lastConsValues = lastConsensus.filter(sc => consVal._1 == sc._1).head._2
          val lastStoredPayout = lastConsValues.getStored

          if(lastStoredPayout + valueFromShares >= currentMinPayout) {

            (consVal._1, lastStoredPayout + valueFromShares)
          } else{

            (consVal._1, 0L)
          }
        }else{
          if(valueFromShares >= currentMinPayout) {
            //println("This new value was higher than min payout" + valueFromShares + " | " + currentMinPayout)
            (consVal._1, valueFromShares)
          } else{
            //println("This new value was lower than min payout: " + valueFromShares + " | " + currentMinPayout)

            (consVal._1, 0L)
          }
        }
    }
    val changeValue =
      currentConsensus.filter(c => c._2.getStored < c._2.getMinPay).dist.map(c => c._2.getStored).sum

    var holdingBuilders = Array.empty[HoldingSetBuilder]
    var tokensUsed = 0L
    boxValueMap.foreach{
      c =>
        val addr = c._1.address
        val addrBytes = c._1.arr

        logger.info(s" Value from shares for address ${addr}: ${c._2}")
        if(c._2 > 0) {
          logger.info(s"Expected token amount: ${(c._2 * tokenRate).toLong}")
          val outB = distributionTx.asUnsignedTxB.outBoxBuilder()
          val holdingBuilder = new HoldingSetBuilder(outB)
          val tokensToAdd = (c._2 * tokenRate).toLong
          val setBuilder = holdingBuilder.value(c._2).contract(new ErgoTreeContract(addr.getErgoAddress.script, addr.getNetworkType))
            .tokens(new ErgoToken(holdingBoxes.head.getTokens.get(0).getId, tokensToAdd))
            .forMiner(true)
          tokensUsed = tokensToAdd + tokensUsed
          holdingBuilders = holdingBuilders++Array(setBuilder)
        }
    }
    logger.info(s"Total tokens used: ${tokensUsed}")
    logger.info(s"Total tokens at start: ${totalTokens}")
    feeAddresses.foreach{
      (addr: Address) =>
        val outB = new HoldingSetBuilder(distributionTx.asUnsignedTxB.outBoxBuilder())
        val addrBytes = PropBytes.ofAddress(addr)
        val boxValue = feeList.filter(f => f._1 == addrBytes).head
        if(boxValue._2 > 0) {
          logger.info(s"Fee Value for address ${addr}: ${boxValue._2}")
          val holdingBuilder = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script, addr.getNetworkType))
          holdingBuilders = holdingBuilders++Array(holdingBuilder)
        }
    }

    if(changeValue > 0) {
      val outB = new HoldingSetBuilder(distributionTx.asUnsignedTxB.outBoxBuilder())
      val tokensToAdd = totalTokens - tokensUsed
      logger.info(s"Tokens left for change: ${tokensToAdd}")
      logger.info(s"Tokens expected by rate: ${(changeValue * tokenRate).toLong}")
      val holdingBuilder = outB.value(changeValue)
        .contract(new ErgoTreeContract(holdingAddress.getErgoAddress.script, holdingAddress.getNetworkType))
        .tokens(new ErgoToken(holdingBoxes.head.getTokens.get(0).getId, tokensToAdd))
      holdingBuilders = holdingBuilders++Array(holdingBuilder)
    }
    new HoldingOutputBuilder(holdingBuilders)
  }



}
object AdditiveHoldingContract {
  val logger: Logger = LoggerFactory.getLogger("AdditiveHoldingContract")
  val script: String = Scripts.ADD_HOLDING_SCRIPT

  /**
   * Generates Holding Contract with given constants
   * @param ctx Blockchain context used to generate contract
   * @param metadataAddress address of metadata
   * @return Compiled ErgoContract of Holding Smart Contract
   */
  def generateHoldingContract(ctx: BlockchainContext, metadataAddress: Address, subpoolToken: ErgoId): HoldingContract = {
    val metadataPropBytes: PropBytes = PropBytes.ofAddress(metadataAddress)(ctx.getNetworkType)
    val subpoolTokenBytes = Colls.fromArray(subpoolToken.getBytes)
    val constantsBuilder = ConstantsBuilder.create()

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_metadataPropBytes", metadataPropBytes.coll)
      .item("const_smartPoolNFT", subpoolTokenBytes)
      .build(), script)
    new AdditiveHoldingContract(compiledContract)
  }
  //
  //
  //
  //
  def getTxFee(dist: ShareDistribution): Long = {
    Parameters.MinFee * dist.size
  }

  def getValAfterFees(totalRewards: Long, txFee: Long, poolFees: PoolFees): Long = {
    val feeList = poolFees.fees.map{
      // Pool fee is defined as x/100000 of total inputs value.
      poolFee =>
        val feeAmount: Long = (poolFee._2.toLong * totalRewards)/PoolFees.POOL_FEE_CONST.toLong
        val feeNoDust: Long = BoxHelpers.removeDust(feeAmount)
        (poolFee._1 , feeNoDust)
    }
    // Total amount in holding after pool fees and tx fees.
    // This is the total amount of ERG to be distributed to pool members
    val totalValAfterFees = (feeList.toArray.foldLeft(totalRewards){
      (accum, poolFeeVal) => accum - poolFeeVal._2
    })- txFee
    totalValAfterFees
  }


  def getBoxValue(shareNum: Long, totalShares: Long, totalValueAfterFees: Long): Long = {
    if(totalShares != 0) {
      val boxValue = ((totalValueAfterFees * BigDecimal(shareNum)) / BigDecimal(totalShares)).toLong
      val dustRemoved = BoxHelpers.removeDust(boxValue)
      dustRemoved
    } else
      0L
  }



}


