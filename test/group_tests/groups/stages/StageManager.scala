package io.getblok.subpooling
package group_tests.groups.stages

import group_tests.groups.{entities, models}
import org.ergoplatform.appkit.SignedTransaction

import scala.util.{Failure, Success}

class StageManager extends models.ManagerBase {
  override val managerName: String = "StageManager"

  def execute[Output](stage: models.TransactionStage[Output]): (Map[entities.Subpool, Output], SignedTransaction) = {
    var result: Map[entities.Subpool, Output] = Map.empty[entities.Subpool, Output]
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
