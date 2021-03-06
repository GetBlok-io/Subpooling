package io.getblok.subpooling_core
package contracts.command

import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}

import io.getblok.subpooling_core.transactions.DistributionTx
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract

class PKContract(p2pkAddress: Address) extends CommandContract(new ErgoTreeContract(p2pkAddress.getErgoAddress.script, p2pkAddress.getNetworkType)) {

  /**
   * A simple P2PK Command Contract performs no specifically coded changes to the command box. Therefore,
   * we may simply return the command output builder that was passed in.
   */
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder = commandOutputBuilder

  /**
   * A simple P2PK Command Contract performs no specifically coded changes to the holding outputs. Therefore,
   * we may simply return the holding output builder that was passed in.
   */
  override def applyToHolding(distributionTx: DistributionTx): HoldingOutputBuilder = distributionTx.holdingOutputBuilder

  override def toAddress: Address = p2pkAddress
}


