package configs

import play.api.Configuration

class SubpoolActorConfig(config: Configuration){
  val numQuickQueries: Int = config.get[Int]("subpool-actors.quick-query-readers.num")
  val numExplorerHandler: Int = config.get[Int]("subpool-actors.explorer-handlers.num")
  val numBlockingUpdateWriters: Int = config.get[Int]("subpool-actors.blocking-db-writers.num")
  val numGroupReqHandlers: Int = config.get[Int]("subpool-actors.group-req-handlers.num")
}
