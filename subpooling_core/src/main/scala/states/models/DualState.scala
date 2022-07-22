package io.getblok.subpooling_core
package states.models

import contracts.plasma.{BalanceStateContract, PlasmaScripts}
import plasma.{BalanceState, DualBalance, SingleBalance}

import io.getblok.getblok_plasma.collections.OpResult
import org.ergoplatform.appkit._

case class DualState(override val box: InputBox, override val balanceState: BalanceState[DualBalance], override val boxes: Seq[InputBox])
  extends State(box, balanceState, boxes){

  def output(ctx: BlockchainContext, poolOp: Address, optValue: Option[Long] = None): OutBox = {
    BalanceStateContract.buildBox(ctx, balanceState, ErgoId.create(poolTag), poolOp, PlasmaScripts.DUAL ,Some(optValue.getOrElse(box.getValue)))
  }

  override def isZeroed(opResult: OpResult[DualBalance]): Boolean = opResult.tryOp.get.get.balance == 0L && opResult.tryOp.get.get.balanceTwo == 0L

  override def zeroed: DualBalance = DualBalance(0L, 0L)

  override def copyState(_box: InputBox, _balanceState: BalanceState[DualBalance], _boxes: Seq[InputBox]): State[DualBalance] = {
    this.copy(_box, _balanceState, _boxes)
  }
}
