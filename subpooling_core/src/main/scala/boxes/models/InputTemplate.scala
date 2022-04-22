package io.getblok.subpooling_core
package boxes.models

import global.AppParameters
import registers._

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.Values
import special.collection.Coll

import java.{lang, util}

abstract class InputTemplate(inputBox: InputBox) extends InputBox{
  final val asInput = this.inputBox
  implicit val networkType: NetworkType = AppParameters.networkType
  val shareDistribution: ShareDistribution = ShareDistribution.ofColl(asInput.getRegisters.get(0).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Long])]])
  val poolFees: PoolFees = PoolFees.ofColl(asInput.getRegisters.get(1).getValue.asInstanceOf[Coll[(Coll[Byte], Int)]])
  val poolInfo: PoolInfo = PoolInfo.ofColl(asInput.getRegisters.get(2).getValue.asInstanceOf[Coll[Long]])
  val poolOps: PoolOperators = PoolOperators.ofColl(asInput.getRegisters.get(3).getValue.asInstanceOf[Coll[Coll[Byte]]])
  val contract: ErgoContract = new ErgoTreeContract(asInput.getErgoTree, networkType)
  val metadataRegisters: MetadataRegisters = MetadataRegisters(shareDistribution, poolFees, poolInfo, poolOps)

  def getValue: Long = asInput.getValue

  def getTokens: util.List[ErgoToken] = asInput.getTokens

  def getRegisters: util.List[ErgoValue[_]] = asInput.getRegisters

  def getErgoTree: Values.ErgoTree = asInput.getErgoTree

  def withContextVars(variables: ContextVar*): InputBox = asInput.withContextVars(variables:_*)

  def toJson(prettyPrint: Boolean): String = asInput.toJson(prettyPrint)

  def toJson(prettyPrint: Boolean, formatJson: Boolean): String = asInput.toJson(prettyPrint, formatJson)

  override def getCreationHeight: Int = asInput.getCreationHeight

  override def getId: ErgoId = asInput.getId

  def getPoolInfo: PoolInfo = {
    poolInfo
  }

  def epoch: Long = {
    poolInfo.getEpoch
  }

  def epochHeight: Long ={
    poolInfo.getEpochHeight
  }

  def genesis: Long ={
    poolInfo.getGenesisHeight
  }

  def subpool: Long ={
    poolInfo.getSubpool
  }

  def getShareDistribution: ShareDistribution = {
    shareDistribution
  }

  def getPoolFees: PoolFees ={
    poolFees
  }
  def getPoolOperators: PoolOperators ={
    poolOps
  }
  def getContract: ErgoContract = {
    contract
  }
  // Returns copy of metadata registers, since InputBox is immutable (and we don't want to imply that registers of the
  // box can change)
  def getMetadataRegisters: MetadataRegisters = {
    metadataRegisters.copy()
  }
}
