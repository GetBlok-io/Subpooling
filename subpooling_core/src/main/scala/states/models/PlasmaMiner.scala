package io.getblok.subpooling_core
package states.models

import plasma.{PartialStateMiner, SingleBalance, StateMiner}

import org.ergoplatform.appkit.Address

case class PlasmaMiner(miner: Address, shareScore: Long, balance: Long, amountAdded: Long, minPay: Long, sharePerc: Double, shareNum: Long,
                       epochsMined: Long) {
  def toStateMiner: StateMiner = StateMiner(miner.toString)

  def toStateBalance: SingleBalance = SingleBalance(balance)
  def toUpdateStateBalance: SingleBalance = SingleBalance(amountAdded)

  def getNextBalance(totalScore: Long, totalReward: Long): Long = (shareScore * totalReward) / totalScore

  def toStateValues: (StateMiner, SingleBalance) = toStateMiner -> toStateBalance

  def toUpdateStateValues: (PartialStateMiner, SingleBalance) = toStateMiner.toPartialStateMiner -> toUpdateStateBalance

}
