package contracts

import app.AppParameters
import boxes.builders.MetadataOutputBuilder
import boxes.{CommandInputBox, MetadataOutBox}
import contracts.Models.Scripts
import org.ergoplatform.appkit._
import registers.{MemberInfo, MetadataRegisters, PoolFees, PoolInfo, PoolOperators, PropBytes, ShareDistribution}
import sigmastate.Values



case class MetadataContract(contract: ErgoContract){
  import MetadataContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = script
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}

object MetadataContract {
  private val constants = ConstantsBuilder.create().build()
  val script: String = Scripts.METADATA_SCRIPT
  def generateMetadataContract(ctx: BlockchainContext): ErgoContract = {
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  /**
   * Generate test script without one epoch per height limitation
   */
  def generateTestContract(ctx: BlockchainContext): ErgoContract = {
    val testScript = Scripts.METADATA_TEST_SCRIPT
    val contract: ErgoContract = ctx.compileContract(constants, testScript)
    contract
  }

  /**
   * Builds genesis box for SmartPool
   *
   * @param mOB    OutBox builder supplied by context
   * @param metadataContract Contract to use for output
   * @param poolOp           Address of initial pool operator
   * @param initialValue     initial value to keep in metadata box
   * @param currentHeight    current height from blockchain context
   * @return OutBox representing new metadata box with initialized registers.
   */
  def buildGenesisBox(mOB: MetadataOutputBuilder, metadataContract: ErgoContract, poolOp: Address, initialValue: Long, currentHeight: Int, subpoolToken: ErgoId, subpoolId: Long): MetadataOutBox = {
    val poolOpBytes = PropBytes.ofAddress(poolOp)(AppParameters.networkType)
    val memberInfo = new MemberInfo(Array(1L, (1 * Parameters.OneErg)/10 , 0L, 0L, 0L))
    val initialConsensus: ShareDistribution = new ShareDistribution(Map((poolOpBytes, memberInfo)))
    val initialPoolFee: PoolFees = new PoolFees(Map((poolOpBytes, 1000)))
    val initialPoolOp: PoolOperators = new PoolOperators(Array(poolOpBytes))
    // The following info is stored: epoch 0, currentEpochHeight, creationEpochHeight,
    // and a filler value for the box id, since that info can only be obtained after the first spending tx.
    val initialPoolInfo: PoolInfo = new PoolInfo(Array(0L, currentHeight.toLong, currentHeight.toLong, subpoolId, 0L))
    val initialMetadata = MetadataRegisters(initialConsensus, initialPoolFee, initialPoolInfo, initialPoolOp)
    mOB
      .contract(metadataContract)
      .value(initialValue)
      .creationHeight(currentHeight)
      .setMetadata(initialMetadata)
      .setSubpoolToken(subpoolToken)
    mOB.build()
  }

  /**
   * Build a MetadataOutBox with the given Smart Pool registers and Smart Pool ID
   *
   * @param metadataContract ErgoContract to make metadata template box under
   * @param initialValue     Value to use for output box
   * @return New MetadataOutBox with given registers and parameters set
   */
  def buildMetadataBox(mOB: MetadataOutputBuilder, metadataContract: ErgoContract, initialValue: Long,
                       metadataRegisters: MetadataRegisters, subpoolToken: ErgoId): MetadataOutBox = {
    mOB
      .contract(metadataContract)
      .value(initialValue)
      .setSubpoolToken(subpoolToken)
      .setMetadata(metadataRegisters)
      .build()
  }


  /**
   * Build a MetadataOutBox with the given Smart Pool registers and Smart Pool ID
   *
   * @param metadataContract ErgoContract to make metadata template box under
   * @return New MetadataOutBox with given registers and parameters set
   */
  def buildFromCommandBox(mOB: MetadataOutputBuilder, commandBox: CommandInputBox, metadataContract: ErgoContract,
                          value: Long, subpoolToken: ErgoId): MetadataOutBox = {
    mOB
      .contract(metadataContract)
      .value(value)
      .setSubpoolToken(subpoolToken)
      .setMetadata(commandBox.getMetadataRegisters)
      .build()
  }

}

