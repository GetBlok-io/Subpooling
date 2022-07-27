package configs

import io.getblok.subpooling_core.explorer.ExplorerHandler
import okhttp3.OkHttpClient
import org.ergoplatform.appkit.NetworkType
import play.api.Configuration

import java.util.concurrent.TimeUnit

class ExplorerConfig(config: Configuration) {
  private val networkType = NetworkType.valueOf(config.get[String]("node.networkType"))
  private val customURL   = config.get[String]("node.explorerURL")


  def explorerHandler = new ExplorerHandler(networkType, Some(customURL))
}
