package io.getblok.subpooling_core
package global

import boxes.BoxHelpers

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.Values.ErgoTree

object AppParameters {

  case class NodeWallet(pk: PK, prover: ErgoProver) {
    val p2pk: Address = pk.p2pk
    val contract: ErgoContract = pk.contract
  }

  case class PK(p2pk: Address) {
    val contract: ErgoContract = new ErgoTreeContract(p2pk.getErgoAddress.script, AppParameters.networkType)
    val tree: ErgoTree = p2pk.getErgoAddress.script
    val bytes: Array[Byte] = p2pk.getErgoAddress.script.bytes
  }

  var networkType: NetworkType = NetworkType.MAINNET
  val groupFee: Long = Parameters.MinFee * 5
  val commandValue: Long = Parameters.MinFee * 5
  var pplnsWindow: BigDecimal = BigDecimal("0.5")
  val shareConst: BigDecimal = BigDecimal("256")
  var scriptBasePath: String = ""
  var mcPoolId: String = "ergo1"
  var defaultMinPay: Long = Parameters.MinFee * 10
  var scoreAdjustmentCoeff: Long = 10000000L

  val baseFeePerc = Map(Address.create("3WwF1KHM9LJyF6T7RVeXgwvbCEsxpho7EMsmtKYBb3KrFs1vRowN") -> 1.0)

  def getBaseFees(blockReward: Long): Map[Address, Long] = baseFeePerc.map(f => f._1 -> BoxHelpers.removeDust((BigDecimal(blockReward) * (f._2 / 100)).longValue()))
}
