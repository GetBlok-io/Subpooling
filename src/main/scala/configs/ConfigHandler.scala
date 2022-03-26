package configs

import com.google.gson.GsonBuilder
import configs.node.{SmartPoolAPIConfig, SmartPoolNodeConfig, SmartPoolWalletConfig}
import configs.params.SmartPoolParameters
import org.ergoplatform.appkit.NetworkType

import java.io.FileWriter

object ConfigHandler {
  final val defaultURL = "http://127.0.0.1:9052/"
  final val defaultConfigName = "sp_config.json"


  def newConfig: SmartPoolConfig = {
    val params = new SmartPoolParameters("", "", Array(""))

    val api = new SmartPoolAPIConfig(defaultURL, "")
    val wallet = new SmartPoolWalletConfig("/secret/storage/path", "wallet_password")
    val node = new SmartPoolNodeConfig(api, wallet, NetworkType.TESTNET)
    val persistence = new PersistenceConfig("127.0.0.1", 9000, "db_name", false)
    val logging = new LoggingConfig(3, 10000)
    val failures = new FailuresConfig()
    val config = new SmartPoolConfig(node, params, persistence, logging, failures)

    config
  }

  def writeConfig(fileName: String, conf: SmartPoolConfig): SmartPoolConfig = {
    val gson = new GsonBuilder().setPrettyPrinting().create()
    val fileWriter = new FileWriter(fileName)
    gson.toJson(conf, fileWriter)
    fileWriter.close()
    conf
  }
}