package io.getblok.subpooling
package core.groups.selectors

import core.groups.entities.Pool
import core.groups.models.GroupSelector

class EmptySelector extends GroupSelector{

  override def getSelection: Pool = {
    pool
  }
}
