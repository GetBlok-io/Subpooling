package io.getblok.subpooling_core
package states.models

import plasma.{DualBalance, PartialStateMiner, SingleBalance, StateMiner}

import org.ergoplatform.appkit.Address

case class PlasmaMiner(miner: Address, shareScore: Long, balance: Long, amountAdded: Long, minPay: Long, sharePerc: Double, shareNum: Long,
                       epochsMined: Long, balanceTwo: Long = 0L, addedTwo: Long = 0L) {
  def toStateMiner: StateMiner = StateMiner(miner.toString)

  def toSingleBalance: SingleBalance = SingleBalance(balance)
  def toUpdateSingleBalance: SingleBalance = SingleBalance(amountAdded)

  def toDualBalance: DualBalance = DualBalance(balance, balanceTwo)
  def toUpdateDualBalance: DualBalance = DualBalance(amountAdded, addedTwo)

  def getNextBalance(totalScore: Long, totalReward: Long): Long = (shareScore * totalReward) / totalScore

  def toSingleStateValues: (StateMiner, SingleBalance) = toStateMiner -> toSingleBalance
  def toDualStateValues: (StateMiner, DualBalance) = toStateMiner -> toDualBalance

  def toUpdateSingleValues: (PartialStateMiner, SingleBalance) = toStateMiner.toPartialStateMiner -> toUpdateSingleBalance
  def toUpdateDualValues:    (PartialStateMiner, DualBalance) = toStateMiner.toPartialStateMiner -> toUpdateDualBalance
}
