package group_tests.groups.models

import group_tests.groups.entities
import group_tests.groups.entities.Member

abstract class GroupSelector(members: Array[Member]) {
  var pool: entities.Pool = _

  def setPool(groupPool: entities.Pool): GroupSelector = {
    pool = groupPool
    this
  }
  /**
   * Place miners with share score > 0 into subPools, while incrementing epochsMined
   */
  def placeCurrentMiners: GroupSelector

  /**
   * Place miners with share score == 0 into subPools so long as their epochsMined is above the limit.
   * Otherwise, remove the miner from the subPool by setting their minimum payment to the minimum
   */
  def evaluateLostMiners: GroupSelector

  /**
   * Place new miners that did not exist in any previous subPools. Miners are first placed into
   * currently used pools below the member limit. If any miners remain, completely new pools are used.
   */
  def placeNewMiners: GroupSelector

  /**
   * Modify the given pool object to reflect only the pools to be used in the Transaction Group
   */
  def getSelection: entities.Pool
}
