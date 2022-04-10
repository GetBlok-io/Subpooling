package io.getblok.subpooling_core
package groups.models

import groups.entities.Pool

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
