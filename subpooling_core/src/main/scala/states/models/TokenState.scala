package io.getblok.subpooling_core
package states.models

import contracts.plasma.{BalanceStateContract, PlasmaScripts}
import plasma.{BalanceState, SingleBalance}

import io.getblok.getblok_plasma.collections.OpResult
import org.ergoplatform.appkit._

import scala.util.Try

case class TokenState(override val box: InputBox, override val balanceState: BalanceState[SingleBalance], override val boxes: Seq[InputBox],
                      tokenId: ErgoId)
  extends State(box, balanceState, boxes){

  def output(ctx: BlockchainContext, poolOp: Address, optValue: Option[Long] = None, optToken: Option[ErgoToken] = None): OutBox = {
    BalanceStateContract.buildBox(ctx, balanceState, ErgoId.create(poolTag), poolOp, PlasmaScripts.SINGLE_TOKEN, Some(optValue.getOrElse(box.getValue)),
      tryGetToken(optToken))
  }

  def token: Option[ErgoToken] = Try(box.getTokens.get(1)).toOption

  def tryGetToken(optToken: Option[ErgoToken]): Option[ErgoToken] = {
    if(optToken.isDefined)
      Some(optToken.get)
    else
      token
  }

  def getTokenValue: Long = Try(box.getTokens.get(1).getValue).getOrElse(0L)

  def addToken(amount: Long): ErgoToken = {
    new ErgoToken(tokenId, getTokenValue + amount)
  }

  def removeToken(amount: Long): ErgoToken = {
    new ErgoToken(tokenId, getTokenValue - amount)
  }
  override def isZeroed(opResult: OpResult[SingleBalance]): Boolean = opResult.tryOp.get.get.balance == 0L

  override def zeroed: SingleBalance = SingleBalance(0L)

  override def copyState(_box: InputBox, _balanceState: BalanceState[SingleBalance], _boxes: Seq[InputBox]): State[SingleBalance] = {
    this.copy(_box, _balanceState, _boxes)
  }
}
