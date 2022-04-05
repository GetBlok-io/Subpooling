package io.getblok.subpooling
package core.groups.stages

import core.groups.entities.Subpool
import core.groups.models.{ManagerBase, TransactionStage}

import org.ergoplatform.appkit.SignedTransaction

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
