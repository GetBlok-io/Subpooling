package configs

import io.getblok.subpooling_core.explorer.ExplorerHandler
import org.ergoplatform.appkit.NetworkType
import play.api.Configuration

class ExplorerConfig(config: Configuration) {
  private val networkType = NetworkType.valueOf(config.get[String]("node.networkType"))

  def explorerHandler = new ExplorerHandler(networkType)
}
