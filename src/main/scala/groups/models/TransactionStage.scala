package groups.models

import app.AppParameters.NodeWallet
import groups.entities.{Pool, Subpool}
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

import scala.util.{Failure, Try}

abstract class TransactionStage[Output](pool: Pool, ctx: BlockchainContext, wallet: NodeWallet){
  var result:           Try[Map[Subpool, Output]] = Failure(new Exception("Empty Result"))
  var transaction:      Try[SignedTransaction]    = Failure(new Exception("Empty Tx"))
  val stageName:        String

  def executeStage:     TransactionStage[Output]
}
