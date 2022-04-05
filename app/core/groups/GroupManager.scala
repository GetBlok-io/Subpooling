package io.getblok.subpooling
package core.groups

import core.groups.entities.Subpool
import core.groups.models.{GroupBuilder, GroupSelector, ManagerBase, TransactionGroup}

import org.ergoplatform.appkit.SignedTransaction

import scala.util.Try


class GroupManager(group: TransactionGroup, builder: GroupBuilder, selector: GroupSelector) extends ManagerBase{
  override protected val managerName: String = "GroupManager"

  var completedGroups: Map[Subpool, SignedTransaction] = Map.empty[Subpool, SignedTransaction]
  var failedGroups:    Map[Subpool, Throwable]         = Map.empty[Subpool, Throwable]
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
