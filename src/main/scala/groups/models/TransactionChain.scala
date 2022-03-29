package groups.models

import app.AppParameters.NodeWallet
import groups.entities.{Pool, Subpool}
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

import scala.util.Try

abstract class TransactionChain[Output](pool: Pool, ctx: BlockchainContext, wallet: NodeWallet){
  val chainName: String
  var resultSet: Map[Subpool, Try[(SignedTransaction, Output)]] = Map.empty[Subpool, Try[(SignedTransaction, Output)]]

  def executeChain: TransactionChain[Output]
}
