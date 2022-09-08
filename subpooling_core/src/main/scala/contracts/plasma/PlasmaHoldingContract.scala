package io.getblok.subpooling_core
package contracts.plasma

import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import org.ergoplatform.appkit.{Address, BlockchainContext, ConstantsBuilder, ErgoContract, ErgoId}
import scorex.crypto.hash.Blake2b256
import sigmastate.eval.Colls
import special.collection.Coll

object PlasmaHoldingContract {

  def generate(ctx: BlockchainContext, shareOp: Address, poolNFT: ErgoId, scriptType: ScriptType): ErgoContract = {
    val updateBalanceContract = UpdateBalanceContract.generate(ctx, poolNFT, scriptType)
    val updateHashed = Blake2b256.hash(updateBalanceContract.getErgoTree.bytes)
    val hashedColl = Colls.fromArray(updateHashed).asInstanceOf[Coll[java.lang.Byte]]
    val constants = ConstantsBuilder.create()
      .item("const_shareOpPK", shareOp.getPublicKey)
      .item("const_hashedUpdateBytes", hashedColl)
      .build()

    val script = {
      scriptType match{
       case PlasmaScripts.SINGLE_TOKEN =>
          PlasmaScripts.TOKEN_HOLDING_SCRIPT
       case _ =>
          PlasmaScripts.HOLDING_SCRIPT
      }
    }
    ctx.compileContract(constants, script)
  }
}
