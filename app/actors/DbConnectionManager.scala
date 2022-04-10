package actors

import actors.DbConnectionManager.{ConnectionResponse, NewConnectionRequest}
import actors.GroupRequestHandler.{DistributionResponse, ExecuteDistribution, PoolData}
import akka.actor.{Actor, Props}
import configs.DbConfig
import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, SimpleHoldingContract}
import io.getblok.subpooling_core.groups.builders.DistributionBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.LoadingSelector
import io.getblok.subpooling_core.groups.{DistributionGroup, GroupManager}
import io.getblok.subpooling_core.persistence.models.Models.{Block, DbConn, PoolMember, PoolPlacement, PoolState}
import org.ergoplatform.appkit.{BlockchainContext, ErgoId, NetworkType}
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer

class DbConnectionManager @Inject()(config: Configuration) extends Actor{

  private val dbConfig: DbConfig = new DbConfig(config)

  private val logger:     Logger     = Logger("DbConnectionManager")

  override def receive: Receive = {
    case NewConnectionRequest =>
      logger.info(s"Received new connection request from ${sender().path.name}")
      val newConnection = dbConfig.getNewConnection
      logger.info("New connection successfully created.")
      sender() ! ConnectionResponse(newConnection)
  }
}

object DbConnectionManager {
  def props: Props = Props[DbConnectionManager]
  sealed trait ConnectionRequest
  case object NewConnectionRequest extends ConnectionRequest

  case class ConnectionResponse(connection: DbConn)
}


