package group_tests.groups.models

import app.AppParameters.NodeWallet
import group_tests.groups.entities
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

import scala.util.{Failure, Try}

abstract class TransactionStage[Output](pool: entities.Pool, ctx: BlockchainContext, wallet: NodeWallet){
  var result:           Try[Map[entities.Subpool, Output]] = Failure(new Exception("Empty Result"))
  var transaction:      Try[SignedTransaction]    = Failure(new Exception("Empty Tx"))
  val stageName:        String

  def executeStage:     TransactionStage[Output]
}
