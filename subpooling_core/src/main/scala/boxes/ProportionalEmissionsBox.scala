package io.getblok.subpooling_core
package boxes

import contracts.emissions.{ExchangeContract, ProportionalEmissionsContract}
import global.{AppParameters, Helpers}
import registers.{LongReg, PropBytes}

import org.ergoplatform.appkit._
import sigmastate.Values

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 * Wrapper class, taking input box and attempting to form some EmissionExchange box out of it
 * @param box Box to form emissions box from
 */
class ProportionalEmissionsBox(box: InputBox, emissionsContract: ProportionalEmissionsContract){
  val asInput: InputBox = box
  val contract: ProportionalEmissionsContract = emissionsContract
  require(this.getTokens.size >= 2)
  require(this.getRegisters.size >= 2)
  require(this.getTokens.head.getValue == 1)
  require(this.getAddress.equals(emissionsContract.getAddress))

  val emissionId: ErgoId = this.getTokens.head.getId
  val distTokenId:    ErgoId = this.getTokens(1).getId
  val numTokens: Long    = this.getTokens(1).getValue.toLong

  val proportion: LongReg   = LongReg(this.getRegisters.head.getValue.asInstanceOf[Long])
  val poolFee: LongReg   = LongReg(this.getRegisters(1).getValue.asInstanceOf[Long])


  def getId: ErgoId = box.getId

  def getValue: Long = box.getValue.toLong

  def getCreationHeight: Int = box.getCreationHeight

  def getTokens: Seq[ErgoToken] = box.getTokens.asScala.toSeq

  def getRegisters: Seq[ErgoValue[_]] = box.getRegisters.asScala.toSeq

  def getErgoTree: Values.ErgoTree = box.getErgoTree

  def getAddress: Address = PropBytes.ofErgoTree(getErgoTree)(AppParameters.networkType).address

  def withContextVars(variables: ContextVar*): InputBox = box.withContextVars(variables:_*)

  def toJson(prettyPrint: Boolean): String = box.toJson(prettyPrint)

  def toJson(prettyPrint: Boolean, formatJson: Boolean): String = box.toJson(prettyPrint, formatJson)

  def getBytes: Array[Byte] = box.getBytes

  override def toString: String = {
    s"EmissionsBox[#${Helpers.trunc(this.emissionId.toString)}]" +
      s"\n(Proportion: ${this.proportion})" +
      s"\n(Total: ${this.numTokens})#${Helpers.trunc(this.distTokenId.toString)}"
  }
}
