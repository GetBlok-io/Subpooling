package io.getblok.subpooling
package group_tests.groups.models

import group_tests.groups.entities

abstract class GroupSelector {
  var pool: entities.Pool = _

  def setPool(groupPool: entities.Pool): GroupSelector = {
    pool = groupPool
    this
  }

  /**
   * Modify the given pool object to reflect only the pools to be used in the Transaction Group
   */
  def getSelection: entities.Pool
}
