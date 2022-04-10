package controllers

import configs._
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.persistence.{BlocksTable, SettingsTable, SharesTable, StateTable}
import io.getblok.subpooling_core.persistence.models.Models.DbConn
import org.ergoplatform.appkit.ErgoClient
import play.api.Configuration
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{BaseController, ControllerComponents, Result}

import javax.inject.Inject

class SubpoolBaseController @Inject()(val controllerComponents: ControllerComponents, config: Configuration)
extends BaseController{
  val dbConfig      = new DbConfig(config)
  val nodeConfig    = new NodeConfig(config)
  val paramsConfig  = new ParamsConfig(config)

  val dbConn: DbConn     = dbConfig.getNewConnection
  val client: ErgoClient = nodeConfig.getClient
  val wallet: NodeWallet = nodeConfig.getNodeWallet


  val blocksTable   = new BlocksTable(dbConn)
  val sharesTable   = new SharesTable(dbConn)
  val stateTable    = new StateTable(dbConn)
  val settingsTable = new SettingsTable(dbConn)

  def okJSON[T](o: T)(implicit tjs: Writes[T]): Result = {
    Ok(Json.prettyPrint(Json.toJson(o)))
  }
}
