package io.getblok.subpooling_core
package groups.models

import groups.entities.{Pool, Subpool}

import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

import scala.util.Try

abstract class TransactionChain[Output](pool: Pool, ctx: BlockchainContext, wallet: NodeWallet) {
  val chainName: String
  var resultSet: Map[Subpool, Try[(SignedTransaction, Output)]] = Map.empty[Subpool, Try[(SignedTransaction, Output)]]

  def executeChain: TransactionChain[Output]
}
