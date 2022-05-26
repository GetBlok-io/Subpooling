import actors.{BlockingDbWriter, DbConnectionManager, ExplorerRequestBus, GroupRequestHandler, PushMessageNotifier, QuickDbReader}
import akka.actor.Props
import akka.routing.RoundRobinPool
import com.google.inject.AbstractModule
import configs.{Contexts, SubpoolActorConfig}
import play.api.{Configuration, Environment}
import play.api.inject.Binding
import play.api.libs.concurrent.AkkaGuiceSupport
import tasks.{BlockStatusCheck, DbCrossCheck, GroupExecutionTask, InitializePoolTask, PoolBlockListener}
class Module(environment: Environment, configuration: Configuration) extends AbstractModule with AkkaGuiceSupport{
  @Override
  override def configure(): Unit = {
    val subpoolActorConfig = new SubpoolActorConfig(configuration)

    bindActor[GroupRequestHandler]("group-handler", p => p.withDispatcher("subpool-contexts.group-dispatcher")
      .withRouter(new RoundRobinPool(subpoolActorConfig.numGroupReqHandlers)))
    bindActor[QuickDbReader]("quick-db-reader", p => p.withDispatcher("subpool-contexts.quick-query-dispatcher")
      .withRouter(new RoundRobinPool(subpoolActorConfig.numQuickQueries)))
    bindActor[ExplorerRequestBus]("explorer-req-bus", p => p.withDispatcher("subpool-contexts.blocking-io-dispatcher")
      .withRouter(new RoundRobinPool(subpoolActorConfig.numExplorerHandler)))
    bindActor[BlockingDbWriter]("blocking-db-writer", p => p.withDispatcher("subpool-contexts.blocking-io-dispatcher")
      .withRouter(new RoundRobinPool(subpoolActorConfig.numBlockingUpdateWriters)))
    bindActor[PushMessageNotifier]("push-msg-notifier", p => p.withDispatcher("subpool-contexts.quick-query-dispatcher"))
    bind[BlockStatusCheck](classOf[BlockStatusCheck]).asEagerSingleton()
    bind[GroupExecutionTask](classOf[GroupExecutionTask]).asEagerSingleton()
    bind[PoolBlockListener](classOf[PoolBlockListener]).asEagerSingleton()
    bind[DbCrossCheck](classOf[DbCrossCheck]).asEagerSingleton()
    bind[InitializePoolTask](classOf[InitializePoolTask]).asEagerSingleton()
  }
}
