package io.getblok.subpooling_core
package states.models

import plasma.BalanceState

import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract
import org.ergoplatform.appkit.{BlockchainContext, ErgoId, InputBox, OutBox}
import scorex.crypto.authds.ADDigest

import scala.collection.mutable.ArrayBuffer

case class State(box: InputBox, balanceState: BalanceState, boxes: Seq[InputBox]) {
  def poolTag: String = box.getTokens.get(0).getId.toString
  def digest: ADDigest = balanceState.map.digest
  def output(ctx: BlockchainContext, optValue: Option[Long] = None): OutBox = {
    BalanceStateContract.buildStateBox(ctx, balanceState, ErgoId.create(poolTag), Some(optValue.getOrElse(box.getValue)))
  }
}
