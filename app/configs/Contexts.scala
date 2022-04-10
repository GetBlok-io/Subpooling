package configs

import akka.actor.ActorSystem

import javax.inject.Inject
import scala.concurrent.ExecutionContext


class Contexts @Inject() (system: ActorSystem) {
  val groupContext: ExecutionContext = system.dispatchers.lookup("group-dispatcher")
}
