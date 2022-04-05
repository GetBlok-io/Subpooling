package io.getblok.subpooling
package core.groups.models

import core.groups.entities.Pool

abstract class GroupSelector {
  var pool: Pool = _

  def setPool(groupPool: Pool): GroupSelector = {
    pool = groupPool
    this
  }
  /**
   * Modify the given pool object to reflect only the pools to be used in the Transaction Group
   */
  def getSelection: Pool
}
