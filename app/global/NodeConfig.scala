package io.getblok.subpooling
package global

import org.ergoplatform.appkit.{ErgoClient, ErgoProver, NetworkType, RestApiErgoClient, SecretStorage}
import play.api.Configuration
import AppParameters._

class NodeConfig(config: Configuration) {
  private val nodeURL: String = config.get[String]("node.url")
  private val nodeKey: String = config.get[String]("node.key")

  private val storagePath: String = config.get[String]("node.storagePath")
  private val password:    String = config.get[String]("node.pass")
  private val networkType: NetworkType = NetworkType.valueOf(config.get[String]("node.networkType"))
  private val secretStorage: SecretStorage = SecretStorage.loadFrom(storagePath)


  AppParameters.networkType = getNetwork
  secretStorage.unlock(password)
  private val prover: ErgoProver = getClient.execute{
    ctx =>
      ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
  }

  private val nodeWallet: NodeWallet = NodeWallet(PK(prover.getEip3Addresses.get(0)), prover)

  def getNetwork: NetworkType   = networkType
  def getExplorerURL: String    = RestApiErgoClient.getDefaultExplorerUrl(networkType)
  def getClient: ErgoClient     = RestApiErgoClient.create(nodeURL, networkType, nodeKey, getExplorerURL)
  def getNodeWallet: NodeWallet = nodeWallet

}
