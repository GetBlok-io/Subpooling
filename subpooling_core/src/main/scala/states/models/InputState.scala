package io.getblok.subpooling_core
package states.models

import contracts.plasma.BalanceStateContract

import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, OutBox}
import scorex.crypto.authds.ADDigest

trait InputState {
  def poolTag: String
  def poolNFT: ErgoId
  def digest: ADDigest

}
