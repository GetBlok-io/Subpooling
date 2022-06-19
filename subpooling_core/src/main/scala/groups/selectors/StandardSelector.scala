package io.getblok.subpooling_core
package groups.selectors

import groups.entities.{Member, Pool}
import registers.{MemberInfo, PropBytes, ShareDistribution}

import io.getblok.subpooling_core.groups.models.GroupSelector
import org.ergoplatform.appkit.Parameters
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer

class StandardSelector(val members: Array[Member], selectionParams: SelectionParameters) extends GroupSelector {
  private val logger: Logger = LoggerFactory.getLogger("StandardSelector")
  final val MEMBER_LIMIT = selectionParams.maxMemberLimit
  final val EPOCH_MINED_LIMIT = selectionParams.epochMinedLimit
  final val KICKED_PAYMENT_THRESHOLD = Parameters.MinFee

  var membersAdded: ArrayBuffer[Member] = ArrayBuffer.empty[Member]
  var membersRemoved: ArrayBuffer[Member] = ArrayBuffer.empty[Member]
  logger.info(s"Total of ${members.length} members are ready for selection")
  // TODO: Add maps for unused and removed pools
  def placeCurrentMiners: GroupSelector = {
    val currentPools = pool.subPools.filter(p => p.epoch > 0)
    for (subPool <- currentPools) {
      var distMap = Map.empty[PropBytes, MemberInfo]

      for (oldMember <- subPool.members) {
        if (members.exists(m => m.address == oldMember.address && oldMember.shareScore > 0)) {
          val currentMember = members.find(m => m.address == oldMember.address).get

          // Increment number by 1 if member was mining previously, otherwise reset value to 1
          val epochsMined = if (oldMember.epochsMined > 0) oldMember.epochsMined + 1 else 1
          val minPay      = if(oldMember.storedPay == Parameters.MinFee) Parameters.MinFee else currentMember.minPay
          distMap = distMap + currentMember.copy(memberInfo
          = currentMember.memberInfo
              .withEpochs(epochsMined)
              .withMinPay(minPay)
          ).toDistributionValue
          membersAdded += currentMember
        }
      }

      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist = nextDist
    }
    this
  }

  def evaluateLostMiners: GroupSelector = {
    val currentPools = pool.subPools.filter(p => p.epoch > 0)
    for (subPool <- currentPools) {
      var distMap = subPool.nextDist.dist
      for (oldMember <- subPool.members) {

        if (members.exists(m => m.address == oldMember.address && oldMember.shareScore == 0 && oldMember.storedPay > 0)) {
          val lostMember = members.find(m => m.address == oldMember.address).get

          // If member was mining last epoch, set to 0, otherwise decrement number into negatives
          val epochsMined = if (oldMember.epochsMined > 0) 0 else oldMember.epochsMined - 1
          if (epochsMined > EPOCH_MINED_LIMIT) {
            distMap = distMap + lostMember.copy(memberInfo
            = lostMember.memberInfo
                .withEpochs(epochsMined)
            ).toDistributionValue
            membersAdded += lostMember
          } else {
            // When epoch mined limit is reached, kick out member by setting payment threshold to bare minimum
            if (epochsMined == EPOCH_MINED_LIMIT) {
              distMap = distMap + lostMember.copy(memberInfo
              = lostMember.memberInfo
                  .withEpochs(epochsMined)
                  .withMinPay(KICKED_PAYMENT_THRESHOLD)
              ).toDistributionValue
              membersAdded += lostMember
            } else {
              membersRemoved += lostMember
            }
          }
        } else if((!members.exists(_.address == oldMember.address)) && oldMember.storedPay > 0){
          if(oldMember.epochsMined > EPOCH_MINED_LIMIT){
            val epochsMined = if (oldMember.epochsMined > 0) 0 else oldMember.epochsMined - 1
            val copiedOldMember = oldMember.copy(memberInfo
            = oldMember.memberInfo
                .withEpochs(epochsMined)
                .withScore(0)
            )
            distMap = distMap + copiedOldMember.toDistributionValue
            membersAdded += copiedOldMember
          }else if(oldMember.epochsMined == EPOCH_MINED_LIMIT){
            val epochsMined = if (oldMember.epochsMined > 0) 0 else oldMember.epochsMined - 1
            val copiedOldMember = oldMember.copy(memberInfo
            = oldMember.memberInfo
                .withEpochs(epochsMined)
                .withScore(0)
                .withMinPay(KICKED_PAYMENT_THRESHOLD)
            )
            distMap = distMap + copiedOldMember.toDistributionValue
            membersAdded += copiedOldMember
          }else{
            membersRemoved += oldMember
          }
        }
      }
      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist = nextDist
    }
    this
  }

  def placeNewMiners: GroupSelector = {
    val currentFreePools = pool.subPools.filter(p => p.epoch > 0 && p.nextDist.size < MEMBER_LIMIT)
    var freeMembers = members.diff(membersAdded).diff(membersRemoved)
    // First select from currently used pools
    for (subPool <- currentFreePools) {
      var distMap = subPool.nextDist.dist

      for (freeMember <- freeMembers) {
        if (distMap.size < MEMBER_LIMIT) {
          distMap = distMap + freeMember.copy(memberInfo
          = freeMember.memberInfo
              .withEpochs(1)
          ).toDistributionValue
          membersAdded += freeMember
        }
      }

      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist = nextDist
      // Reset free members to re-evaluate next loop
      freeMembers = members.diff(membersAdded).diff(membersRemoved)
    }

    val newFreePools = pool.subPools.filter(p => p.epoch == 0)
    var membersLeft = members.diff(membersAdded).diff(membersRemoved)
    logger.info(s"Total of ${membersLeft.length} members left after placement in existing pools. Now placing into new pools.")
    for (subPool <- newFreePools) {
      var distMap = Map.empty[PropBytes, MemberInfo]

      for (freeMember <- membersLeft) {
        logger.info(s"Now evaluating member ${freeMember.address.toString} with info ${freeMember.memberInfo.toString}")
        if (distMap.size < MEMBER_LIMIT) {
          distMap = distMap + freeMember.copy(memberInfo
          = freeMember.memberInfo
              .withEpochs(1)
          ).toDistributionValue
          membersAdded += freeMember
        }
      }

      val nextDist = new ShareDistribution(distMap)
      subPool.nextDist = nextDist
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


    logger.info("Pools were selected!")
    logger.info(s"Num pools: ${pool.subPools.length}")
    logger.info(s"Pool distributions: ")
    pool.subPools.foreach(s => logger.info(s.nextDist.toString))

    pool
  }
}
