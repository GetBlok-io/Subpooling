package io.getblok.subpooling_core
package cycles.models

import persistence.models.PersistenceModels.PoolPlacement

import org.ergoplatform.appkit.InputBox

trait Cycle {
  def simulateSwap: EmissionResults
  def getEmissionsBox: InputBox
  def morphPlacementValues(placements: Seq[PoolPlacement], emissionResults: EmissionResults): Seq[PoolPlacement]
  def morphPlacementHolding(placements: Seq[PoolPlacement], holdingBox: InputBox): Seq[PoolPlacement]
  def cycle(cycleState: CycleState, emissionResults: EmissionResults, sendTxs: Boolean = true): CycleResults
}
