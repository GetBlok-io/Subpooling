package io.getblok.subpooling_core
package states.models

import plasma.{PartialStateMiner, StateBalance, StateMiner}

import org.ergoplatform.appkit.Address

case class PlasmaMiner(miner: Address, shareScore: Long, balance: Long, amountAdded: Long, minPay: Long, sharePerc: Double, shareNum: Long,
                       epochsMined: Long) {
  def toStateMiner: StateMiner = StateMiner(miner.toString)

  def toStateBalance: StateBalance = StateBalance(balance)
  def toUpdateStateBalance: StateBalance = StateBalance(amountAdded)

  def getNextBalance(totalScore: Long, totalReward: Long): Long = (shareScore * totalReward) / totalScore

  def toStateValues: (StateMiner, StateBalance) = toStateMiner -> toStateBalance

  def toUpdateStateValues: (PartialStateMiner, StateBalance) = toStateMiner.toPartialStateMiner -> toUpdateStateBalance

}
