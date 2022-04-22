package io.getblok.subpooling_core
package boxes

import io.getblok.subpooling_core.contracts.emissions.EmissionsContract
import io.getblok.subpooling_core.global
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.registers.{LongReg, PropBytes}
import org.ergoplatform.appkit.{Address, ContextVar, ErgoId, ErgoToken, ErgoValue, InputBox, NetworkType}
import sigmastate.Values
import special.collection.Coll

import java.{lang, util}
import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 * Wrapper class, taking input box and attempting to form some EmissionsBox out of it
 * @param box Box to form emissions box from
 */
class EmissionsBox(box: InputBox, emissionsContract: EmissionsContract){
  val asInput: InputBox = box
  val contract: EmissionsContract = emissionsContract
  require(this.getTokens.size >= 2)
  require(this.getRegisters.size >= 2)
  require(this.getTokens.head.getValue == 1)
  require(this.getAddress.equals(emissionsContract.getAddress))

  val emissionId: ErgoId = this.getTokens.head.getId
  val tokenId:    ErgoId = this.getTokens(1).getId
  val numTokens: Long    = this.getTokens(1).getValue.toLong

  val emissionReward: LongReg   = LongReg(this.getRegisters.head.getValue.asInstanceOf[Long])
  val ergHolder:   PropBytes = PropBytes.ofColl(this.getRegisters(1).getValue.asInstanceOf[Coll[Byte]])(AppParameters.networkType)


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
    s"EmissionsBox[#${Helpers.trunc(this.emissionId.toString)}][Reward: ${this.emissionReward}#${Helpers.trunc(this.tokenId.toString)}]" +
      s"\n(Exchange: ${this.ergHolder})" +
      s"\n(Total: ${this.numTokens})#${Helpers.trunc(this.tokenId.toString)}"
  }
}
