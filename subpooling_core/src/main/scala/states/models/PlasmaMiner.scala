package io.getblok.subpooling_core
package states.models

import plasma.{StateBalance, StateMiner}

import org.ergoplatform.appkit.Address

case class PlasmaMiner(miner: Address, shareScore: Long, balance: Long, amountAdded: Long, paid: Boolean, index: Long) {
  def getStateMiner: StateMiner = StateMiner(miner.toString)

  def getStateBalance: StateBalance = StateBalance(balance)

  def getNextBalance(totalScore: Long, totalReward: Long): Long = (shareScore * totalReward) / totalScore

  def toStateValues: (StateMiner, StateBalance) = getStateMiner -> getStateBalance


}
