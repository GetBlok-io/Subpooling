package plasma_utils.payments

import io.getblok.subpooling_core.cycles.models.NFTHolder
import models.ResponseModels.{ExchangeRateHolder, readsNFTHolder}
import play.api.libs.ws.WSClient

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

object ExRateUtils {

  def getExRate(ws: WSClient)(implicit ec: ExecutionContext): Double = {
    Await.result(ws.url("https://rates.runonflux.io/api/fluxrate").get().map{
      resp =>

        (resp.json).validate[ExchangeRateHolder]
    }, 1000 seconds).get.rateData.exRate.ergRate
  }
}
