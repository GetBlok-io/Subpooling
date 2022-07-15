package io.getblok.subpooling_core
package states.models

import plasma.BalanceState

import io.getblok.subpooling_core.contracts.plasma.BalanceStateContract
import org.ergoplatform.appkit.{BlockchainContext, InputBox, OutBox}

import scala.collection.mutable.ArrayBuffer

case class State(box: InputBox, balanceState: BalanceState, boxes: Seq[InputBox]) {
  def poolTag: String = box.getTokens.get(0).getId.toString
  def output(ctx: BlockchainContext, optValue: Option[Long] = None): OutBox = {
    BalanceStateContract.buildStateBox(ctx, balanceState, Some(optValue.getOrElse(box.getValue)))
  }
}
