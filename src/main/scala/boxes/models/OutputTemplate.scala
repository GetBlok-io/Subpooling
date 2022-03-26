package boxes.models

import app.AppParameters
import org.ergoplatform.appkit._
import registers._
import special.collection.Coll

/**
 * Wrapper class that wraps output boxes as metadata boxes / command boxes
 *
 * @param outBox Out box to wrap as metadata box / command box
 */
abstract class OutputTemplate(outBox: OutBox, metadataRegs: MetadataRegisters) extends OutBox{
  final val asOutBox = this.outBox
  implicit val networkType: NetworkType = AppParameters.networkType
  val metadataRegisters: MetadataRegisters = metadataRegs
  val shareDistribution: ShareDistribution = metadataRegisters.shareDist
  val poolFees: PoolFees = metadataRegisters.feeMap
  val poolInfo: PoolInfo = metadataRegisters.poolInfo
  val poolOps: PoolOperators = metadataRegisters.poolOps

  def getValue: Long = asOutBox.getValue

  def convertToInputWith(txId: String, boxIdx: Short): InputBox = asOutBox.convertToInputWith(txId, boxIdx)

  override def getTokens: java.util.List[ErgoToken] = asOutBox.getTokens

  def getMetadataRegisters: MetadataRegisters = {
    metadataRegisters
  }

  def getPoolInfo: PoolInfo = {
    poolInfo
  }

  def epoch: Long = {
    poolInfo.getEpoch
  }

  def epochHeight: Long ={
    poolInfo.getEpochHeight
  }

  def genesis: Long = {
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

  override def getCreationHeight: Int = asOutBox.getCreationHeight

  override def toString: String = {
    val asString = s"""
    Output Template Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - Epoch: ${this.epoch}
    - Epoch Height: ${this.epochHeight}
    - Creation Height: ${this.getCreationHeight}
    - Creation ID: ${this.subpool}
    """
    asString
  }

}
