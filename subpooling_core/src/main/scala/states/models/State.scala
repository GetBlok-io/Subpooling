package io.getblok.subpooling_core
package states.models

import plasma.{BalanceState, SingleBalance}

import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox, OutBox}
import scorex.crypto.authds.ADDigest

import scala.collection.mutable.ArrayBuffer

case class State(override val box: InputBox, override val balanceState: BalanceState[SingleBalance], override val boxes: Seq[InputBox])
  extends InputState[SingleBalance](box, balanceState, boxes) {
  override def poolTag: String = box.getTokens.get(0).getId.toString
  override def poolNFT: ErgoId = box.getTokens.get(0).getId
  override def digest: ADDigest = balanceState.map.digest

  override def output(ctx: BlockchainContext, poolOp: Address, optValue: Option[Long] = None): OutBox = {
    BalanceStateContract.buildStateBox(ctx, balanceState, ErgoId.create(poolTag), poolOp, Some(optValue.getOrElse(box.getValue)))
  }
}
