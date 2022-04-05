package io.getblok.subpooling
package core.boxes.builders

import core.boxes.HoldingOutBox
import core.contracts.command.CommandContract
import core.transactions.DistributionTx

/**
 * Outbox Builder wrapper that builds holding outputs. Each long value represents the value of the holding output,
 * while the boolean indicates whether the output is for a miner, or is a fee or change box from the tx.
 */
class HoldingOutputBuilder(builders: Array[HoldingSetBuilder]) {

  def applyCommandContract(distributionTx: DistributionTx, commandContract: CommandContract): HoldingOutputBuilder = {
    commandContract.applyToHolding(distributionTx)
   this
 }

  def getBuilders: Array[HoldingSetBuilder] = builders

  def build(): List[HoldingOutBox] = {
    val holdingOutputsBuilt = builders.map(oBB => oBB.build).toList
    holdingOutputsBuilt
  }

}


