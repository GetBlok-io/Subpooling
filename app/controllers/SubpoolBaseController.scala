package io.getblok.subpooling
package controllers

import core.persistence._
import core.persistence.models.Models._
import global.AppParameters._
import global._

import org.ergoplatform.appkit.ErgoClient
import play.api.Configuration
import play.api.mvc.{BaseController, ControllerComponents}

import javax.inject.Inject

class SubpoolBaseController @Inject()(val controllerComponents: ControllerComponents, config: Configuration)
extends BaseController{
  val dbConfig      = new DbConfig(config)
  val nodeConfig    = new NodeConfig(config)

  val dbConn: DbConn     = dbConfig.getConnection
  val client: ErgoClient = nodeConfig.getClient
  val wallet: NodeWallet = nodeConfig.getNodeWallet


  val blocksTable   = new BlocksTable(dbConn)
  val sharesTable   = new SharesTable(dbConn)
  val stateTable    = new StateTable(dbConn)
  val settingsTable = new SettingsTable(dbConn)

}
