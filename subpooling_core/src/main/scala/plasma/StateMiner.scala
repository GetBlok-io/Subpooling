package io.getblok.subpooling_core
package plasma

import org.ergoplatform.appkit.Address
import scorex.crypto.hash.Blake2b256
import sigmastate.Values

case class StateMiner(miner: String) {
  val address: Address = Address.create(miner)
  def toErgoTree: Values.ErgoTree = address.getErgoAddress.script
  def toPartialStateMiner: PartialStateMiner = PartialStateMiner(Blake2b256.hash(toErgoTree.bytes))
  def hexString: String = toPartialStateMiner.toString
  override def toString: String = s"StMiner[${miner}](${toPartialStateMiner.toString})"
}
