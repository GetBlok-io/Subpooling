package io.getblok.subpooling_core
package states.models

import plasma.{BalanceState, SingleBalance}

import io.getblok.getblok_plasma.collections.OpResult
import io.getblok.subpooling_core.contracts.plasma.{BalanceStateContract, PlasmaScripts}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox, OutBox}
import scorex.crypto.authds.ADDigest

import scala.collection.mutable.ArrayBuffer

case class SingleState(override val box: InputBox, override val balanceState: BalanceState[SingleBalance], override val boxes: Seq[InputBox])
  extends State(box, balanceState, boxes){

  def output(ctx: BlockchainContext, poolOp: Address, optValue: Option[Long] = None): OutBox = {
    BalanceStateContract.buildBox(ctx, balanceState, ErgoId.create(poolTag), poolOp, PlasmaScripts.SINGLE, Some(optValue.getOrElse(box.getValue)))
  }

  override def isZeroed(opResult: OpResult[SingleBalance]): Boolean = opResult.tryOp.get.get.balance == 0L

  override def zeroed: SingleBalance = SingleBalance(0L)

  override def copyState(_box: InputBox, _balanceState: BalanceState[SingleBalance], _boxes: Seq[InputBox]): State[SingleBalance] = {
    this.copy(_box, _balanceState, _boxes)
  }
}
