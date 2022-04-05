package io.getblok.subpooling
package core.groups.models

import global.AppParameters.NodeWallet
import core.groups.entities.Pool

import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}
import scala.util.Try

import core.groups.entities.{Pool, Subpool}

abstract class TransactionChain[Output](pool: Pool, ctx: BlockchainContext, wallet: NodeWallet){
  val chainName: String
  var resultSet: Map[Subpool, Try[(SignedTransaction, Output)]] = Map.empty[Subpool, Try[(SignedTransaction, Output)]]

  def executeChain: TransactionChain[Output]
}
