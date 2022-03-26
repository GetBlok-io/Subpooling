package contracts.command

import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import transactions.DistributionTx

class PKContract(p2pkAddress: Address) extends CommandContract(new ErgoTreeContract(p2pkAddress.getErgoAddress.script, p2pkAddress.getNetworkType)) {

  override def getAddress: Address = p2pkAddress

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
}


