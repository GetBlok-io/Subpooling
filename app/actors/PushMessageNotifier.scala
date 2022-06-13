package actors

import actors.GroupRequestHandler.{DistributionResponse, ExecuteDistribution, PoolData}
import actors.PushMessageNotifier.{BlockMessage, MessageRequest}
import akka.actor.{Actor, Props}
import configs.{NodeConfig, ParamsConfig}
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, SimpleHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.builders.DistributionBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.LoadingSelector
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager}
import io.getblok.subpooling_core.persistence.models.Models.{Block, PoolBlock, PoolInformation, PoolMember, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, ErgoId, NetworkType}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Message dispatch for telegram bots
 */
class PushMessageNotifier @Inject()(wsClient: WSClient, config: Configuration) extends Actor{

  private val logger: Logger     = Logger("MessageDispatcher")
  val params = new ParamsConfig(config)
  logger.info("Initiating MessageDispatcher")
  val blockBotChat: String = params.blockBotChat
  val blockBotAPI = s"https://api.telegram.org/bot${params.blockBotToken}/sendMessage"
  override def receive: Receive = {
    case message: MessageRequest =>
      message match {
        case BlockMessage(poolBlock, poolInfo) =>
          if(params.enableBlockBot) {
            logger.debug(s"Sending Push Notification for block ${poolBlock.blockheight}")
            val msgString = s"Block ${poolBlock.blockheight} was found by miner:\n${poolBlock.miner}\nin pool:\n#[${Helpers.trunc(poolBlock.poolTag)}]\n(${poolInfo.title})" +
              s"\nThe pool has now found ${poolBlock.gEpoch} blocks." +
              s"\nThe block's effort was ${(poolBlock.effort.get.toFloat * 100).toString}%"
            val request: WSRequest = wsClient.url(blockBotAPI)
            val resp = request
              .withQueryStringParameters("chat_id" -> s"$blockBotChat", "text" -> msgString)
              .withRequestTimeout(30 seconds)
              .get()

            resp.onComplete{
              case Success(value) =>
                logger.debug(s"response: ${value.body}")
              case Failure(exception) =>
                logger.error("Fatal error during block notification", exception)
            }(context.dispatcher)


          }
      }
  }

}

object PushMessageNotifier {
  def props: Props = Props[PushMessageNotifier]

  sealed trait MessageRequest

  case class BlockMessage(poolBlock: PoolBlock, poolInfo: PoolInformation) extends MessageRequest

  case class FailureMessage(failure: Failure[_], message: String) extends MessageRequest
}


