package io.getblok.subpooling_core
package states.groups

import io.getblok.subpooling_core.persistence.models.Models.PoolMember
import io.getblok.subpooling_core.states.models.TransformResult

import scala.util.Try

trait StateGroup {
  var transformResults: Seq[Try[TransformResult]]
  def setup(): Unit
  def applyTransformations(): Try[Unit]
  def sendTransactions: Seq[Try[TransformResult]]
  def getMembers: Seq[PoolMember]
}
