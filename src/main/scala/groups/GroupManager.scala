package groups

import groups.models.{GroupBuilder, GroupSelector, TransactionGroup}

import scala.util.Try

class GroupManager(group: TransactionGroup, builder: GroupBuilder, selector: GroupSelector) {

  def initiate(): Unit = {
    val trySelect     = Try(group.selectForGroup(selector))
    val tryBuild      = trySelect.map(g => g.buildGroup(builder))
    val tryExecution  = tryBuild.map(g => g.executeGroup)

    if(trySelect.isFailure){
      // TODO: Logger
    }else if(tryBuild.isFailure){
      // TODO: Logger
    }else if(tryExecution.isFailure){
      // TODO: Logger
    }
  }
}
