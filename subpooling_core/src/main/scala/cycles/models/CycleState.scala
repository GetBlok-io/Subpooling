package io.getblok.subpooling_core
package cycles.models

import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolPlacement
import org.ergoplatform.appkit.InputBox

case class CycleState(cycleBox: InputBox, placements: Seq[PoolPlacement], reward: Long, fee: Long) {

}