package io.getblok.subpooling
package global

import play.api.Configuration

import java.sql.DriverManager
import java.util.Properties
import core.persistence.models.Models.DbConn

class DbConfig(config: Configuration){
  private val baseURL = "jdbc:postgresql:"
  private val user = config.get[String]("persistence.user")
  private val pass = config.get[String]("persistence.pass")
  private val port = config.get[Int]("persistence.port")
  private val host = config.get[String]("persistence.host")
  private val dbURL = baseURL + s"//$host:$port/"

  private val properties = new Properties()
  properties.setProperty("user", user)
  properties.setProperty("password", pass)
  properties.setProperty("ssl", "false")
  private val dbConn = DbConn(DriverManager.getConnection(dbURL, properties))

  def getConnection: DbConn = dbConn
}
