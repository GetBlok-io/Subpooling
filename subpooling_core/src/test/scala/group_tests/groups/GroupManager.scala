package io.getblok.subpooling_core
package group_tests.groups

import org.ergoplatform.appkit.SignedTransaction

import scala.util.Try

class GroupManager(group: models.TransactionGroup, builder: models.GroupBuilder, selector: models.GroupSelector) extends models.ManagerBase{
  override protected val managerName: String = "GroupManager"

  var completedGroups: Map[entities.Subpool, SignedTransaction] = Map.empty[entities.Subpool, SignedTransaction]
  var failedGroups:    Map[entities.Subpool, Throwable]         = Map.empty[entities.Subpool, Throwable]
  var isSuccess:       Boolean                         = false

  def initiate(): Unit = {
    val trySelect     = Try(group.selectForGroup(selector))
    val tryBuild      = trySelect.map(g => g.buildGroup(builder))
    val tryExecution  = tryBuild.map(g => g.executeGroup)

    if(trySelect.isFailure){
      logger.error("Error thrown during Group Selection")
      logStacktrace(trySelect.failed.get)
    }else if(tryBuild.isFailure){
      logger.error("Error thrown during Group Building")
      logStacktrace(tryBuild.failed.get)
    }else if(tryExecution.isFailure){
      logger.error("Error thrown during Group Execution")
      logStacktrace(tryExecution.failed.get)
    }else{
      isSuccess = true
      completedGroups = tryExecution.get.completedGroups
      failedGroups    = tryExecution.get.failedGroups
    }
  }


}
