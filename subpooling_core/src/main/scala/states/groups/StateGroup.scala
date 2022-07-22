package io.getblok.subpooling_core
package states.groups

import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolMember
import io.getblok.subpooling_core.plasma.{PoolBalanceState, StateBalance}
import io.getblok.subpooling_core.states.models.TransformResult

import scala.util.Try

trait StateGroup[T <: StateBalance] {
  var transformResults: Seq[Try[TransformResult[T]]]
  def setup(): Unit
  def applyTransformations(): Try[Unit]
  def sendTransactions: Seq[Try[TransformResult[T]]]
  def getMembers: Seq[PoolMember]
  def getPoolBalanceStates: Seq[PoolBalanceState]
}
