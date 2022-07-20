package configs

import play.api.Configuration

import java.sql.DriverManager
import java.util.Properties
import io.getblok.subpooling_core.persistence.models.PersistenceModels.DbConn

class DbConfig(config: Configuration){
  private val baseURL = "jdbc:postgresql:"
  private val user = config.get[String]("persistence.user")
  private val pass = config.get[String]("persistence.pass")
  private val port = config.get[Int]("persistence.port")
  private val host = config.get[String]("persistence.host")
  private val name = config.get[String]("persistence.name")
  private val fullURL = config.get[String]("persistence.optFullURL")
  private val dbURL = baseURL + s"//$host:$port/$name"

  private val properties = new Properties()
  properties.setProperty("user", user)
  properties.setProperty("password", pass)
  properties.setProperty("ssl", "false")

  def getNewConnection: DbConn = DbConn(DriverManager.getConnection(fullURL))
}
