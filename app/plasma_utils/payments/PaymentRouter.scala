package plasma_utils.payments

import io.getblok.subpooling_core.persistence.models.Models.{MinerSettings, PoolInformation}
import models.DatabaseModels.SMinerSettings
import persistence.shares.ShareCollector
import utils.ConcurrentBoxLoader.BatchSelection

object PaymentRouter {

  def routeProcessor(info: PoolInformation, settings: Seq[SMinerSettings], collector: ShareCollector, batch: BatchSelection, reward: Long): PaymentProcessor = {
    info.currency match {
      case PoolInformation.CURR_ERG =>
        StandardProcessor(settings, collector, batch, reward)
      case _ =>
        throw new Exception("Unsupported currency found during processor routing!")
    }
  }
}
