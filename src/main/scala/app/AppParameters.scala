package app

import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoContract, ErgoProver, NetworkType, Parameters}
import sigmastate.Values.ErgoTree

object AppParameters {

  case class NodeWallet(pk: PK, prover: ErgoProver){
    val p2pk:     Address      = pk.p2pk
    val contract: ErgoContract = pk.contract
  }
  case class PK(p2pk: Address) {
    val contract: ErgoContract = new ErgoTreeContract(p2pk.getErgoAddress.script, AppParameters.networkType)
    val tree:     ErgoTree     = p2pk.getErgoAddress.script
    val bytes:    Array[Byte]  = p2pk.getErgoAddress.script.bytes
  }

  val networkType:  NetworkType = NetworkType.MAINNET
  val groupFee:     Long        = Parameters.MinFee * 5
  val commandValue: Long        = Parameters.MinFee * 5
  val pplnsWindow:  BigDecimal  = BigDecimal("0.5")
  val shareConst:   BigDecimal  = BigDecimal("256")
}
