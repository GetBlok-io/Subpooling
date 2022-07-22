package io.getblok.subpooling_core
package states.models

import contracts.plasma.BalanceStateContract
import plasma.BalanceState

import io.getblok.getblok_plasma.collections.OpResult
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox, OutBox}
import scorex.crypto.authds.ADDigest

abstract class State[T](val box: InputBox, val balanceState: BalanceState[T], val boxes: Seq[InputBox]) {
  def poolTag: String = box.getTokens.get(0).getId.toString
  def poolNFT: ErgoId = box.getTokens.get(0).getId
  def digest: ADDigest = balanceState.map.digest
  def output(ctx: BlockchainContext, poolOp: Address, optValue: Option[Long] = None): OutBox

  def isZeroed(opResult: OpResult[T]): Boolean
  def zeroed: T

  def copyState(_box: InputBox = box, _balanceState: BalanceState[T] = balanceState, _boxes: Seq[InputBox] = boxes): State[T]
}