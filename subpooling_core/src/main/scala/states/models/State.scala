package io.getblok.subpooling_core
package states.models

import plasma.{BalanceState, SingleBalance}

import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox, OutBox}
import scorex.crypto.authds.ADDigest

import scala.collection.mutable.ArrayBuffer

case class State[T](box: InputBox, balanceState: BalanceState[T], boxes: Seq[InputBox]) extends InputState {
  def poolTag: String = box.getTokens.get(0).getId.toString
  def poolNFT: ErgoId = box.getTokens.get(0).getId
  def digest: ADDigest = balanceState.map.digest
  def output(ctx: BlockchainContext, poolOp: Address, optValue: Option[Long] = None): OutBox = {
    BalanceStateContract.buildStateBox(ctx, balanceState, ErgoId.create(poolTag), poolOp, Some(optValue.getOrElse(box.getValue)))
  }
}
