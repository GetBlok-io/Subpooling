package io.getblok.subpooling
package core.groups.selectors

import core.groups.entities.Pool

import org.ergoplatform.appkit.Parameters
import scala.collection.mutable.ArrayBuffer

import core.groups.entities.{Member, Pool}
import core.groups.models.GroupSelector
import core.registers.{MemberInfo, PropBytes, ShareDistribution}

class StandardSelector(members: Array[Member]) extends GroupSelector{

  final val MEMBER_LIMIT = 10
  final val EPOCH_MINED_LIMIT = -5
  final val KICKED_PAYMENT_THRESHOLD = Parameters.MinFee

  var membersAdded: ArrayBuffer[Member] = ArrayBuffer.empty[Member]
  var membersRemoved: ArrayBuffer[Member] = ArrayBuffer.empty[Member]
  // TODO: Add maps for unused and removed pools
  def placeCurrentMiners: GroupSelector = {
    val currentPools = pool.subPools.filter(p => p.epoch > 0)
    for(subPool <- currentPools){
      var distMap = Map.empty[PropBytes, MemberInfo]

      for(oldMember <- subPool.members){
        if(members.exists(m => m.address == oldMember.address && oldMember.shareScore > 0)){
          val currentMember = members.find(m => m.address == oldMember.address).get

          // Increment number by 1 if member was mining previously, otherwise reset value to 1
          val epochsMined = if(oldMember.epochsMined > 0) oldMember.epochsMined + 1 else 1

          distMap = distMap + currentMember.copy(memberInfo
            = currentMember.memberInfo
              .withEpochs(epochsMined)
          ).toDistributionValue
          membersAdded += currentMember
        }
      }

      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist =  nextDist
    }
    this
  }

  def evaluateLostMiners: GroupSelector = {
    val currentPools = pool.subPools.filter(p => p.epoch > 0)
    for(subPool <- currentPools){
      var distMap = subPool.nextDist.dist
      for(oldMember <- subPool.members){

        if(members.exists(m => m.address == oldMember.address && oldMember.shareScore == 0)){
          val lostMember = members.find(m => m.address == oldMember.address).get

          // If member was mining last epoch, set to 0, otherwise decrement number into negatives
          val epochsMined = if(oldMember.epochsMined > 0) 0 else oldMember.epochsMined - 1
          if(epochsMined > EPOCH_MINED_LIMIT) {
            distMap = distMap + lostMember.copy(memberInfo
            = lostMember.memberInfo
                .withEpochs(epochsMined)
            ).toDistributionValue
            membersAdded += lostMember
          }else{
            // When epoch mined limit is reached, kick out member by setting payment threshold to bare minimum
            if(epochsMined == EPOCH_MINED_LIMIT){
              distMap = distMap + lostMember.copy(memberInfo
              = lostMember.memberInfo
                  .withEpochs(epochsMined)
                  .withMinPay(KICKED_PAYMENT_THRESHOLD)
              ).toDistributionValue
              membersAdded += lostMember
            }else{
              membersRemoved += lostMember
            }
          }
        }
      }
      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist = nextDist
    }
    this
  }

  def placeNewMiners: GroupSelector = {
    val currentFreePools = pool.subPools.filter(p => p.epoch > 0 && p.nextDist.size < EPOCH_MINED_LIMIT)
    var freeMembers = members.diff(membersAdded).diff(membersRemoved)
    // First select from currently used pools
    for(subPool <- currentFreePools){
      var distMap = subPool.nextDist.dist

      for(freeMember <- freeMembers){
        if(distMap.size < MEMBER_LIMIT){
          distMap = distMap + freeMember.copy(memberInfo
          = freeMember.memberInfo
              .withEpochs(1)
          ).toDistributionValue
          membersAdded += freeMember
        }
      }

      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist =  nextDist
      // Reset free members to re-evaluate next loop
      freeMembers = members.diff(membersAdded).diff(membersRemoved)
    }

    val newFreePools = pool.subPools.filter(p => p.epoch == 0)
    var membersLeft = members.diff(membersAdded).diff(membersRemoved)

    for(subPool <- newFreePools){
      var distMap = Map.empty[PropBytes, MemberInfo]

      for(freeMember <- membersLeft){
        if(distMap.size < MEMBER_LIMIT){
          distMap = distMap + freeMember.copy(memberInfo
          = freeMember.memberInfo
              .withEpochs(1)
          ).toDistributionValue
          membersAdded += freeMember
        }
      }

      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist =  nextDist
      // Reset membersLeft to re-evaluate next loop
      membersLeft = members.diff(membersAdded).diff(membersRemoved)
    }

    this
  }

  override def getSelection: Pool = {
    placeCurrentMiners
    evaluateLostMiners
    placeNewMiners
    pool.subPools --= pool.subPools.filter(p => p.nextDist == null)
    pool.subPools --= pool.subPools.filter(p => p.nextDist.size == 0)
    pool
  }
}
