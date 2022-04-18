package configs

import akka.actor.ActorSystem

import javax.inject.Inject
import scala.concurrent.ExecutionContext


class Contexts (system: ActorSystem) {
  private val prefix = "subpool-contexts."
  val groupContext:      ExecutionContext = system.dispatchers.lookup(prefix+"group-dispatcher")
  val taskContext:       ExecutionContext = system.dispatchers.lookup(prefix+"task-dispatcher")
  val quickQueryContext: ExecutionContext = system.dispatchers.lookup(prefix+"quick-query-dispatcher")
  val blockingContext:   ExecutionContext = system.dispatchers.lookup(prefix+"blocking-io-dispatcher")
}
