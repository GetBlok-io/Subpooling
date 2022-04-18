package actors

import actors.ExplorerRequestBus.{ExplorerRequest, ExplorerRequests}
import actors.ExplorerRequestBus.ExplorerRequests._
import actors.QuickDbReader._
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.{Actor, Props, SupervisorStrategy}
import configs.{DbConfig, ExplorerConfig, NodeConfig}
import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.persistence.models.Models.DbConn
import io.getblok.subpooling_core.persistence._
import org.ergoplatform.appkit.{Address, ErgoId}
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.language.postfixOps

class ExplorerRequestBus @Inject()(configuration: Configuration) extends Actor{
  private val log: Logger   = Logger("ExplorerRequestBus")
  private val expConfig: ExplorerConfig = new ExplorerConfig(configuration)
  private val nodeConfig: NodeConfig = new NodeConfig(configuration)

  private val handler = expConfig.explorerHandler
  private val nodeAPIHandler = new NodeHandler(nodeConfig.apiClient, nodeConfig.getClient)
  log.info("Initializing a new ExplorerRequestBus")

  override def receive: Receive = {
    case explorerRequest: ExplorerRequest =>

      log.info("New explorer request was received!")

      explorerRequest match {
        case ExplorerRequests.BlockByHash(blockHash) =>
          sender() ! handler.getBlockById(blockHash)
        case TxsForAddress(address, offset, limit) =>
          sender() ! handler.txsForAddress(address, offset, limit)
        case TxById(txId) =>
          sender() ! handler.getTransaction(txId)
        case BoxesByAddress(address, offset, limit) =>
          sender() ! handler.boxesByAddress(address, offset, limit)
        case BoxesByTokenId(tokenId, offset, limit) =>
          sender() ! handler.boxesByTokenId(tokenId, offset, limit)
        case TotalBalance(address) =>
          sender() ! handler.getTotalBalance(address)
        case ConfirmedBalance(address, minConfirms) =>
          sender() ! handler.getConfirmedBalance(address, minConfirms)
          // Node API calls
        case ValidateBlockByHeight(height) =>
        sender ! nodeAPIHandler.validateBlock(height)
        case ExplorerRequests.GetCurrentHeight =>
          sender ! nodeConfig.getClient.execute(ctx => ctx.getHeight)
      }


  }

}

object ExplorerRequestBus {
  def props: Props = Props[ExplorerRequestBus]

  sealed trait ExplorerRequest
  object ExplorerRequests {
    case class BlockByHash(blockHash: ErgoId)                                     extends ExplorerRequest

    case class TxsForAddress(address: Address, offset: Int = 0, limit: Int = 10)  extends ExplorerRequest
    case class TxById(txId: ErgoId)                                               extends ExplorerRequest
    case class BoxesByAddress(address: Address, offset: Int = 0, limit: Int = 10) extends ExplorerRequest
    case class BoxesByTokenId(tokenId: ErgoId, offset: Int = 0, limit: Int = 10)  extends ExplorerRequest
    case class TotalBalance(address: Address)                                     extends ExplorerRequest
    case class ConfirmedBalance(address: Address, minConfirms: Int = 20)          extends ExplorerRequest

    // Single node api and client calls
    case class ValidateBlockByHeight(height: Long)                                extends ExplorerRequest
    case object GetCurrentHeight                                                  extends ExplorerRequest
  }
}




