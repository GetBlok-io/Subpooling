package io.getblok.subpooling
package core.transactions

import core.boxes.BoxHelpers
import core.contracts.holding.HoldingContract
import core.logging.LoggingHandler
import core.transactions.models.TransactionTemplate

import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.seqAsJavaListConverter

class RegroupMultipleTx(unsignedTxBuilder: UnsignedTransactionBuilder, feeAddress: Address) extends TransactionTemplate(unsignedTxBuilder){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_REGROUP_TX)
  private[this] var _feeInputs: List[InputBox] = _
  private[this] var _holdingContract: HoldingContract = _
  private[this] var _holdingInputs: List[InputBox] = _
  private[this] var _newHoldingValues: Array[Long] = Array()
  def feeInputs: List[InputBox] = _feeInputs

  def feeInputs(boxes: InputBox*): RegroupMultipleTx = {
    _feeInputs = boxes.toList
    this
  }


  def holdingContract: HoldingContract = _holdingContract

  def holdingContract(value: HoldingContract): RegroupMultipleTx = {
    _holdingContract = value
    this
  }

  def holdingInputs: List[InputBox] = _holdingInputs

  def holdingInputs(inputs: List[InputBox]): RegroupMultipleTx = {
    _holdingInputs = inputs
    this
  }

  def newHoldingValues: Array[Long] = _newHoldingValues

  def newHoldingValue(value: Long): RegroupMultipleTx = {
    _newHoldingValues = _newHoldingValues++Array(value)
    this
  }


  override def build(): UnsignedTransaction = {
    val heldValue = BoxHelpers.sumBoxes(holdingInputs)
    val regroupedValue = newHoldingValues.sum
    var outputBoxes = List[OutBox]()
    for(holdingVal <- newHoldingValues) {
      val holdingOutbox: OutBox =
        asUnsignedTxB.outBoxBuilder()
          .value(holdingVal)
          .contract(holdingContract.asErgoContract)
          .build()
      outputBoxes = outputBoxes++List(holdingOutbox)
    }

    val valueDelta = Math.abs(heldValue - regroupedValue)

    if(valueDelta > Parameters.MinFee){
      val holdingChangeBox: OutBox =
        asUnsignedTxB.outBoxBuilder()
          .value(valueDelta)
          .contract(holdingContract.asErgoContract)
          .build()
      outputBoxes = outputBoxes++List(holdingChangeBox)
    }

    val txFee = BoxHelpers.sumBoxes(_feeInputs)
    val unsignedTx = asUnsignedTxB
      .boxesToSpend((holdingInputs++feeInputs).asJava)
      .fee(txFee)
      .sendChangeTo(feeAddress.getErgoAddress)
      .outputs(outputBoxes:_*)
      .build()
    unsignedTx
  }
}
