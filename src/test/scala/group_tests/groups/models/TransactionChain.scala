package group_tests.groups.models

import app.AppParameters.NodeWallet
import group_tests.groups.entities
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

import scala.util.Try

abstract class TransactionChain[Output](pool: entities.Pool, ctx: BlockchainContext, wallet: NodeWallet){
  val chainName: String
  var resultSet: Map[entities.Subpool, Try[(SignedTransaction, Output)]] = Map.empty[entities.Subpool, Try[(SignedTransaction, Output)]]

  def executeChain: TransactionChain[Output]
}
