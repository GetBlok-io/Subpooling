package io.getblok.subpooling_core
package transactions

import boxes.builders.HoldingOutputBuilder
import contracts.command.CommandContract
import contracts.holding.{HoldingContract, TokenHoldingContract}

import io.getblok.subpooling_core.boxes.{BoxHelpers, CommandInputBox, MetadataInputBox, MetadataOutBox}
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.logging.LoggingHandler
import io.getblok.subpooling_core.registers.PropBytes
import io.getblok.subpooling_core.transactions.models.MetadataTxTemplate
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class DistributionTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends MetadataTxTemplate(unsignedTxBuilder) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DIST_TX)
  private var hOB: HoldingOutputBuilder = _
  private[this] var _mainHoldingContract: HoldingContract = _
  private[this] var _otherCommandContracts: List[CommandContract] = List[CommandContract]()
  private[this] var _holdingInputs: List[InputBox] = List[InputBox]()
  private[this] var _tokenToDistribute: ErgoToken = _
  private[this] var _operatorAddress: Address = _

  def otherCommandContracts: List[CommandContract] = _otherCommandContracts

  def otherCommandContracts(contracts: List[CommandContract]): Unit = {
    _otherCommandContracts = contracts

  }

  def holdingContract: HoldingContract = _mainHoldingContract

  def holdingContract(holdingContract: HoldingContract): DistributionTx = {
    _mainHoldingContract = holdingContract
    this
  }

  def holdingInputs: List[InputBox] = _holdingInputs

  def holdingInputs(holdingBoxes: List[InputBox]): DistributionTx = {
    _holdingInputs = holdingBoxes
    this
  }

  def holdingOutputBuilder: HoldingOutputBuilder = hOB

  def holdingOutputBuilder(holdingBuilder: HoldingOutputBuilder): DistributionTx = {
    hOB = holdingBuilder
    this
  }


  def tokenToDistribute: ErgoToken = _tokenToDistribute

  def tokenToDistribute(token: ErgoToken): DistributionTx = {
    _tokenToDistribute = token
    this
  }

  def operatorAddress: Address = _operatorAddress

  def operatorAddress(address: Address): DistributionTx = {
    _operatorAddress = address
    this
  }

  def withCommandContracts(commandContracts: CommandContract*): DistributionTx = {
    otherCommandContracts(commandContracts.toList)
    this
  }

  def metadataInput(value: MetadataInputBox): DistributionTx = {
    this._metadataInputBox = value
    this
  }

  def commandInput(value: CommandInputBox): DistributionTx = {
    this._commandInputBox = value
    this
  }

  def metadataOutput(value: MetadataOutBox): DistributionTx = {
    this._metadataOutBox = value
    this
  }

  override def buildMetadataTx(): UnsignedTransaction = {
    val commandContract = commandInputBox.contract
    val holdingAddress = holdingContract.toAddress


    logger.info(s"Total Holding Box Value: ${BoxHelpers.sumBoxes(holdingInputs)}")

    val holdingBoxes = holdingInputs

    val metadataContract = metadataInputBox.getContract

    val initBoxes = List(metadataInputBox.asInput, commandInputBox.asInput)
    val inputBoxes = initBoxes++holdingBoxes

    metadataOutput(MetadataContract.buildFromCommandBox(mOB, commandInputBox, metadataContract, metadataInputBox.getValue, metadataInputBox.subpoolToken))

    hOB = holdingContract
      .generateInitialOutputs(ctx, this, holdingBoxes)

    hOB = commandContract.applyToHolding(this)

    if(holdingContract.isInstanceOf[TokenHoldingContract])
      _tokenToDistribute = new ErgoToken(holdingBoxes.head.getTokens.get(0).getId, holdingBoxes.map(hb => hb.getTokens.get(0).getValue.toLong).sum)
    else
      _tokenToDistribute = null

    var tokensDistributed = 0L
    if(_tokenToDistribute != null) {
      for (outBB <- hOB.getBuilders) {
        logger.info(s"tokensDistributed: $tokensDistributed")
        if (outBB.forMiner) {
          logger.info(s"Adding amount ${outBB.tokenList.head.getValue} to tokens distributed")
          tokensDistributed = tokensDistributed + outBB.tokenList.head.getValue
        }
      }
    }
    //otherCommandContracts.foreach(c => hOB.applyCommandContract(this, c))

    val holdingOutputs = hOB.build()
    var totalTokens = 0L
    for(hb <- holdingOutputs){
      logger.info("Output Box Val: " + hb.asOutBox.getValue)
      if(_tokenToDistribute != null) {

        if (hb.asOutBox.getTokens.asScala.exists(t => t.getId == _tokenToDistribute.getId)) {
          logger.info("Address: " + PropBytes.ofErgoTree(hb.getErgoTree)(ctx.getNetworkType).address.toString)
          logger.info("Holding Box Token: " + hb.asOutBox.getTokens.asScala.find(t => t.getId == _tokenToDistribute.getId).get)
          logger.info("Holding Box Token Amnt: " + hb.getTokens.asScala.find(t => t.getId == _tokenToDistribute.getId).get)
          totalTokens = totalTokens + hb.asOutBox.getTokens.asScala.find(t => t.getId == _tokenToDistribute.getId).get.getValue.toLong
        }
      }
    }
    logger.info(s"Total distributed tokens in tx: $totalTokens")
    var txFee = (commandInputBox.getValue + commandInputBox.shareDistribution.size * Parameters.MinFee)
    if(_tokenToDistribute != null){
      // Ensure there is enough ERG for change box when a token is being distributed
      txFee = commandInputBox.getValue
    }

    val outputBoxes = List(metadataOutBox.asOutBox)++(holdingOutputs.map(h => h.asOutBox))
    val inputIds = inputBoxes.map(ib => ib.getId.toString)
    logger.info("Input Ids: " + inputIds.mkString("( ", " , " , " )"))
    logger.info("Distribution Tx built")
    logger.info("Total Input Value: "+ (inputBoxes.map(x => x.getValue.toLong).sum))
    logger.info("Total Output Value: "+ outputBoxes.map(x => x.getValue.toLong).sum)

      this.asUnsignedTxB
      .boxesToSpend(inputBoxes.asJava)
      .outputs(outputBoxes:_*)
      .fee(txFee)
      .sendChangeTo(operatorAddress.getErgoAddress)
//    // Unused tokens are burnt to prevent abuse.
//    if(_tokenToDistribute != null){
//      val tokensAmntToBurn = commandInputBox.getTokens.get(0).getValue - tokensDistributed
//      val tokensToBurn = new ErgoToken(commandInputBox.getTokens.get(0).getId, tokensAmntToBurn)
//      this.asUnsignedTxB.tokensToBurn(tokensToBurn)
//    }
    this.asUnsignedTxB.build()

  }


}
