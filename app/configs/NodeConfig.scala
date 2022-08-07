package configs


import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.{NodeWallet, PK}
import okhttp3.OkHttpClient
import org.ergoplatform.appkit.{ErgoClient, ErgoProver, NetworkType, RestApiErgoClient, SecretStorage}
import org.ergoplatform.restapi.client.ApiClient
import play.api.Configuration

import java.util.concurrent.TimeUnit

class NodeConfig(config: Configuration) {
  private val nodeURL: String = config.get[String]("node.url")
  private val nodeKey: String = config.get[String]("node.key")

  private val storagePath: String = config.get[String]("node.storagePath")
  private val password:    String = config.get[String]("node.pass")
  private val networkType: NetworkType = NetworkType.valueOf(config.get[String]("node.networkType"))
  private val scriptBase: String = config.get[String]("params.scriptBasePath")
  private val secretStorage: SecretStorage = SecretStorage.loadFrom(storagePath)
  private var explorerURL: String = config.get[String]("node.explorerURL")

  AppParameters.networkType = getNetwork
  AppParameters.scriptBasePath = scriptBase
  secretStorage.unlock(password)
  if(explorerURL == "default")
    explorerURL = RestApiErgoClient.getDefaultExplorerUrl(networkType)


  val httpClientBuilder = new OkHttpClient()
    .newBuilder()
    .callTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .connectTimeout(60, TimeUnit.SECONDS)


  private val ergoClient: ErgoClient = RestApiErgoClient.createWithHttpClientBuilder(nodeURL, networkType, nodeKey, getExplorerURL, httpClientBuilder)
  val apiClient = new ApiClient(nodeURL, "ApiKeyAuth", nodeKey)
  private val prover: ErgoProver = ergoClient.execute{
    ctx =>
      ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
  }
  private val nodeWallet: NodeWallet = NodeWallet(PK(prover.getEip3Addresses.get(0)), prover)


  def getNetwork: NetworkType   = networkType
  def getExplorerURL: String    = explorerURL
  def getClient: ErgoClient     = ergoClient
  def getNodeWallet: NodeWallet = nodeWallet

}
