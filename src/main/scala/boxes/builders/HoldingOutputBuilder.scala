package boxes.builders

import boxes.{CommandOutBox, HoldingOutBox}
import contracts.command.CommandContract
import contracts.holding.HoldingContract
import org.ergoplatform.appkit._
import registers._
import transactions.DistributionTx

/**
 * Outbox Builder wrapper that builds holding outputs. Each long value represents the value of the holding output,
 * while the boolean indicates whether the output is for a miner, or is a fee or change box from the tx.
 */
class HoldingOutputBuilder(builders: Array[HoldingBuilder]) {

  def applyCommandContract(distributionTx: DistributionTx, commandContract: CommandContract): HoldingOutputBuilder = {
    commandContract.applyToHolding(distributionTx)
   this
 }

  def getBuilders: Array[HoldingBuilder] = builders

  def build(): List[HoldingOutBox] = {
    val holdingOutputsBuilt = builders.map(oBB => oBB.build).toList
    holdingOutputsBuilt
  }

}


