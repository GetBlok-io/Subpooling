package io.getblok.subpooling_core
package groups.selectors

import groups.models.GroupSelector

import io.getblok.subpooling_core.groups.entities.Pool

class EmptySelector extends GroupSelector {

  override def getSelection: Pool = {
    pool
  }
}
