package io.getblok.subpooling_core
package group_tests.groups.entities

import boxes.{CommandInputBox, MetadataInputBox}
import group_tests.groups.entities
import registers._

import org.ergoplatform.appkit.{ErgoId, InputBox}

class Subpool(metadataInputBox: MetadataInputBox){
  val box: MetadataInputBox = metadataInputBox
  val info: MetadataRegisters = metadataInputBox.metadataRegisters
  val id: Long = metadataInputBox.poolInfo.getSubpool
  val epoch: Long = metadataInputBox.epoch
  val token: ErgoId = metadataInputBox.subpoolToken
  val tag: Long = metadataInputBox.poolInfo.getTag
  val members: Array[Member] = metadataInputBox.shareDistribution.dist.map(d => entities.Member(d._1.address, d._2)).toArray
  val lastTotalScore: Long = members.map(m => m.shareScore).sum


  // Used during selection
  var nextDist: ShareDistribution = _

  // Used during distribution
  var nextFees: PoolFees = _
  var nextInfo: PoolInfo = _
  var nextOps:  PoolOperators = _
  var paymentMap: Map[PropBytes, InputBox] = Map.empty[PropBytes, InputBox]

  // Used during placement
  var nextHoldingValue: Long = _

  var rootBox: InputBox = _
  var holdingBox: InputBox = _
  var storedBox: Option[InputBox] = None
  var commandBox: CommandInputBox = _
  var nextBox: MetadataInputBox = _

  def nextMembers: Array[Member] = nextDist.dist.map(d => entities.Member(d._1.address, d._2)).toArray
  def nextTotalScore: Long = nextMembers.map(m => m.shareScore).sum


}
