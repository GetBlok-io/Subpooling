package io.getblok.subpooling_core
package states.models

import io.getblok.subpooling_core.plasma.BalanceState
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox, OutBox}
import scorex.crypto.authds.ADDigest

abstract class InputState[T](val box: InputBox, val balanceState: BalanceState[T], val boxes: Seq[InputBox]) {
  def poolTag: String = box.getTokens.get(0).getId.toString
  def poolNFT: ErgoId = box.getTokens.get(0).getId
  def digest: ADDigest = balanceState.map.digest

  def output(ctx: BlockchainContext, poolOp: Address, optValue: Option[Long] = None): OutBox
}
