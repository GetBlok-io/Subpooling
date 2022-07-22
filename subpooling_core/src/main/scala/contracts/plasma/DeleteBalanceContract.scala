package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.getblok_plasma.collections.OpResult
import io.getblok.subpooling_core.contracts.Models.Scripts
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{BalanceState, PartialStateMiner, SingleBalance}
import org.ergoplatform.appkit
import org.ergoplatform.appkit.JavaHelpers.JByteRType
import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ContextVar, ErgoContract, ErgoId, ErgoType, ErgoValue, InputBox, OutBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values
import sigmastate.eval.Colls


case class DeleteBalanceContract(contract: ErgoContract) {
  import DeleteBalanceContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = "script"
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}

object DeleteBalanceContract {


  private val logger: Logger = LoggerFactory.getLogger("DeleteBalanceContract")

  def routeScript(scriptType: ScriptType): String = {
    scriptType match {
      case PlasmaScripts.SINGLE =>
        PlasmaScripts.SINGLE_DELETE_SCRIPT
      case PlasmaScripts.DUAL =>
        PlasmaScripts.HYBRID_DELETE_SCRIPT
    }
  }

  def generate(ctx: BlockchainContext, poolNFT: ErgoId, scriptType: ScriptType): ErgoContract = {
    val constants = ConstantsBuilder.create().item("const_poolNFT", Colls.fromArray(poolNFT.getBytes)).build()
    val contract: ErgoContract = ctx.compileContract(constants, routeScript(scriptType))
    contract
  }

  def buildBox(ctx: BlockchainContext, poolNFT: ErgoId, scriptType: ScriptType, optValue: Option[Long] = None): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(optValue.getOrElse(Helpers.MinFee))
      .contract(generate(ctx, poolNFT, scriptType))
      .build()
  }

  def applyContext[T](updateBox: InputBox, balanceState: BalanceState[T], deletes: Seq[PartialStateMiner],
                      zeroed: OpResult[T] => Boolean): InputBox = {
    val deleteType = ErgoType.collType(ErgoType.byteType())
    val deleteErgoVal = ErgoValue.of(Colls.fromArray(deletes.map(_.toColl).toArray), deleteType)

    val lookup = balanceState.map.lookUp(deletes:_*)
    require(lookup.response.forall(zeroed), "Not all balances were zeroed!")

    val result = balanceState.map.delete(deletes:_*)
    logger.info(s"Deleting ${deletes.length} balance states")
    logger.info(s"Lookup Proof size: ${lookup.proof.bytes.length} bytes")
    logger.info(s"Delete Proof size: ${result.proof.bytes.length} bytes")
    logger.info(s"Result: ${result.response.mkString("( ", ", ", " )")}")
    updateBox.withContextVars(
      ContextVar.of(0.toByte, deleteErgoVal),
      ContextVar.of(1.toByte, lookup.proof.ergoValue),
      ContextVar.of(2.toByte, result.proof.ergoValue)
    )
  }
}






