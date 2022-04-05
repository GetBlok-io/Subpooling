package io.getblok.subpooling
package core.groups.models

import global.AppParameters.NodeWallet
import core.groups.entities.Pool

import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}
import scala.util.{Failure, Try}

import core.groups.entities.{Pool, Subpool}

abstract class TransactionStage[Output](pool: Pool, ctx: BlockchainContext, wallet: NodeWallet){
  var result:           Try[Map[Subpool, Output]] = Failure(new Exception("Empty Result"))
  var transaction:      Try[SignedTransaction]    = Failure(new Exception("Empty Tx"))
  val stageName:        String

  def executeStage:     TransactionStage[Output]
}
