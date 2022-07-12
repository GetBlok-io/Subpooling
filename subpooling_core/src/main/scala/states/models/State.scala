package io.getblok.subpooling_core
package states.models

import plasma.BalanceState

import org.ergoplatform.appkit.InputBox

import scala.collection.mutable.ArrayBuffer

case class State(box: InputBox, miners: Seq[PlasmaMiner], balanceState: BalanceState) {
  def reorder(): State = { this.copy(miners = miners.sortBy(s => BigInt(s.getStateMiner.toPartialStateMiner.bytes))) }

}
