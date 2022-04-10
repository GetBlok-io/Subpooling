package io.getblok.subpooling_core
package contracts.command

import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import boxes.{CommandOutBox, MetadataInputBox}
import global.AppParameters
import registers.MetadataRegisters
import transactions.DistributionTx

import org.ergoplatform.appkit._
import sigmastate.Values

abstract class CommandContract(commandContract: ErgoContract) extends ErgoContract {
  override def getConstants: Constants = commandContract.getConstants

  override def getErgoScript: String = commandContract.getErgoScript

  override def substConstant(name: String, value: Any): ErgoContract = commandContract.substConstant(name, value)

  override def getErgoTree: Values.ErgoTree = commandContract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = commandContract

  /**
   * Apply this command contract's effects to an unbuilt command output. Multiple command contracts
   * may apply their effects so as to get the final value of the outputted command box.
   * @param commandOutputBuilder initialized command output builder
   * @return Command output builder with desired command effects on the metadata.
   */
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder

  /**
   * Apply this command contract's effects to an unbuilt command output. Multiple command contracts
   * may apply their effects so as to get the final value of the outputted command box.
   * @param distributionTx distributionTx to apply holding on
   * @return Command output builder with desired command effects on the metadata.
   */
  def applyToHolding(distributionTx: DistributionTx): HoldingOutputBuilder
}

object CommandContract {

  /**
   * Build a CommandOutBox with the given Smart Pool registers
   *
   * @param cOB    builder to wrap
   * @param commandContract ErgoContract to make metadata template box under
   * @param value     Value to use for output box
   * @return New CommandOutBox with given registers
   */
  def buildCommandBox(cOB: CommandOutputBuilder, commandContract: CommandContract, value: Long,
                      metadataRegisters: MetadataRegisters): CommandOutBox = {
    cOB
      .contract(commandContract)
      .value(value)
      .setMetadata(metadataRegisters)
      .build()
  }

  /**
   * Copy the contents of a command outbox into an unbuilt template builder.
   *
   * @param commandOutBox out box to copy from
   * @param outBoxBuilder       outBoxBuilder to build output from
   * @param commandContract    Contract to use for metadata-like output
   * @return New MetadataOutBox with same exact register registers
   */
  def copyCommandOutBox(cOB: CommandOutputBuilder, commandOutBox: CommandOutBox, commandContract: CommandContract): CommandOutputBuilder = {
    cOB
      .contract(commandContract)
      .value(commandOutBox.getValue)
      .setMetadata(commandOutBox.getMetadataRegisters)
  }

  /**
   * Initialize output builder using metadata input box as a register template. This function automatically increases
   * the epoch by 1, sets the current epoch height, as well as setting the command box's value and contract.
   * @param cOB Uninitialized command output builder
   * @param metadataInputBox metadata box to use as a template
   * @param currentHeight current height of blockchain
   * @return Unbuilt command output initialized with information for next transaction.
   */
  def initializeOutputBuilder(cOB: CommandOutputBuilder, metadataInputBox: MetadataInputBox,
                              currentHeight: Int, commandContract: CommandContract, value: Long): CommandOutputBuilder = {
    var commandRegs = metadataInputBox.getMetadataRegisters
    val newPoolInfo = commandRegs.poolInfo.withEpoch(commandRegs.poolInfo.getEpoch + 1).withHeight(currentHeight)

    commandRegs = commandRegs.copy(poolInfo = newPoolInfo)

    cOB
      .contract(commandContract)
      .value(value)
      .setMetadata(commandRegs)
  }

  /**
   * This function automatically increases
   * the epoch by 1, sets the current epoch height
   * @param cOB Uninitialized command output builder
   * @param metadataInputBox metadata box to use as a template
   * @param currentHeight current height of blockchain
   * @return Unbuilt command output initialized with information for next transaction.
   */
  def updatePoolInfo(cOB: CommandOutputBuilder, metadataInputBox: MetadataInputBox,
                              currentHeight: Int): CommandOutputBuilder = {
    val metadataRegs = metadataInputBox.getMetadataRegisters
    val newPoolInfo = metadataRegs.poolInfo.withEpoch(metadataRegs.poolInfo.getEpoch + 1).withHeight(currentHeight)

    var cobMetadata = cOB.metadataRegisters
    cobMetadata = cobMetadata.copy(poolInfo = newPoolInfo)
    cOB
      .setMetadata(cobMetadata)
  }


}

