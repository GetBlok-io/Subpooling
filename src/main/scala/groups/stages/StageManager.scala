package groups.stages

import groups.entities.Subpool
import groups.models.{ManagerBase, TransactionStage}
import org.ergoplatform.appkit.SignedTransaction
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success}

class StageManager extends ManagerBase {
  override val managerName: String = "StageManager"

  def execute[Output](stage: TransactionStage[Output]): (Map[Subpool, Output], SignedTransaction) = {
    var result: Map[Subpool, Output] = Map.empty[Subpool, Output]
    var transaction: SignedTransaction = null

    stage.executeStage

    stage.result match {

      case Success(m) =>
        result = m
        transaction = stage.transaction.get
      case Failure(e: Exception) =>
        logger.error(s"StageManager failed to execute stage ${stage.stageName}")
        logStacktrace(e)
        throw new StageManagerException
      case _ =>
        logger.error("Unknown error thrown during StageExecution")
    }

    result -> transaction
  }
}
