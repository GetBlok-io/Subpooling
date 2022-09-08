package plasma_utils.payments

import io.getblok.subpooling_core.cycles.models.NFTHolder
import models.ResponseModels.readsNFTHolder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

object NFTUtils {

  def getAnetaNFTHolders(ws: WSClient)(implicit ec: ExecutionContext): Seq[NFTHolder] = {
    Await.result(ws.url("https://my.ergoport.dev/cgi-bin/aneta/epAngels.pl?a=0-4444").get().map{
      resp =>

        (resp.json).validate[Seq[NFTHolder]]
    }, 1000 seconds).get
  }
}
