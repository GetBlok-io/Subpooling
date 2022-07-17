package plasma_utils

import akka.actor.ActorRef
import akka.util.Timeout
import configs.{Contexts, NodeConfig, ParamsConfig}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.stages.roots.{EmissionRoot, ExchangeEmissionsRoot, HoldingRoot, ProportionalEmissionsRoot}
import io.getblok.subpooling_core.persistence.models.Models._
import models.DatabaseModels.{SMinerSettings, SPoolBlock}
import org.ergoplatform.appkit.InputBox
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import persistence.shares.ShareCollector
import plasma_utils.payments.PaymentRouter
import plasma_utils.shares.BatchShareCollector
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader
import utils.ConcurrentBoxLoader.BatchSelection

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class TransformValidator(expReq: ActorRef, contexts: Contexts, params: ParamsConfig,
                         nodeConfig: NodeConfig, db: PostgresProfile#Backend#Database) {
  val logger: Logger = LoggerFactory.getLogger("TransformValidator")
  import slick.jdbc.PostgresProfile.api._
  implicit val timeout: Timeout = Timeout(1000 seconds)
  implicit val taskContext: ExecutionContext = contexts.taskContext

  def checkInitiated() = {

  }


}
