package configs


import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.{NodeWallet, PK}
import org.ergoplatform.appkit.{ErgoClient, ErgoProver, NetworkType, RestApiErgoClient, SecretStorage}
import org.ergoplatform.restapi.client.ApiClient
import play.api.Configuration

class NodeConfig(config: Configuration) {
  private val nodeURL: String = config.get[String]("node.url")
  private val nodeKey: String = config.get[String]("node.key")

  private val storagePath: String = config.get[String]("node.storagePath")
  private val password:    String = config.get[String]("node.pass")
  private val networkType: NetworkType = NetworkType.valueOf(config.get[String]("node.networkType"))
  private val scriptBase: String = config.get[String]("params.scriptBasePath")
  private val secretStorage: SecretStorage = SecretStorage.loadFrom(storagePath)


  AppParameters.networkType = getNetwork
  AppParameters.scriptBasePath = scriptBase
  secretStorage.unlock(password)
  private val explorerURL: String = RestApiErgoClient.getDefaultExplorerUrl(networkType)
  private val ergoClient: ErgoClient = RestApiErgoClient.create(nodeURL, networkType, nodeKey, getExplorerURL)
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
