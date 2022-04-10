import actors.{DbConnectionManager, GroupRequestHandler}
import akka.actor.Props
import com.google.inject.AbstractModule
import configs.Contexts
import play.api.{Configuration, Environment}
import play.api.inject.Binding
import play.api.libs.concurrent.AkkaGuiceSupport
class Module(environment: Environment, configuration: Configuration) extends AbstractModule with AkkaGuiceSupport{
  @Override
  override def configure(): Unit = {

    bindActor[GroupRequestHandler]("group-handler", p => p.withDispatcher("startup-pinned-dispatcher"))
    bindActor[DbConnectionManager]("db-conn-manager", p => p.withDispatcher("startup-pinned-dispatcher"))

  }
}
