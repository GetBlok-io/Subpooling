package configs

import play.api.Configuration

import scala.concurrent.duration.{Duration, FiniteDuration}

class TasksConfig(config: Configuration){
  val blockCheckConfig:   TasksConfig.TaskConfiguration = TasksConfig.TaskConfiguration.fromConfig(config, "block-status-check")
  val groupExecConfig:    TasksConfig.TaskConfiguration = TasksConfig.TaskConfiguration.fromConfig(config, "group-execution")
  val poolBlockConfig:    TasksConfig.TaskConfiguration = TasksConfig.TaskConfiguration.fromConfig(config, "pool-block-listener")
  val dbCrossCheckConfig: TasksConfig.TaskConfiguration = TasksConfig.TaskConfiguration.fromConfig(config, "db-cross-check")
}

object TasksConfig {
  case class TaskConfiguration(enabled: Boolean, startup: FiniteDuration, interval: FiniteDuration)
  object TaskConfiguration {
    def fromConfig(configuration: Configuration, name: String): TaskConfiguration = {
      val isEnabled = configuration.get[Boolean](s"subpool-tasks.${name}.enabled")
      val startupTime = configuration.get[FiniteDuration](s"subpool-tasks.${name}.startup")
      val intervalTime = configuration.get[FiniteDuration](s"subpool-tasks.${name}.interval")
      TaskConfiguration(isEnabled, startupTime, intervalTime)
    }
  }
}
