package utils

import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.Models.PoolInformation

object PoolTemplates {
  case class PoolTemplate(title: String, fee: Double, numSubpools: Int, paymentType: PaymentType, emissionsType: String,
                          currency: String, epochKick: Long, maxMembers: Long)

  val STANDARD_POOL: PoolTemplate = PoolTemplate("GetBlok.io Smart Pool", 0.5, 100, PaymentType.PPLNS_WINDOW, PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 5L, 10L)
  val SOLO_POOL: PoolTemplate = PoolTemplate("GetBlok.io Smart Pool SOLO", 0.5, 100,PaymentType.SOLO_SHARES, PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 5L, 10L)
  val TEST_TOKEN_POOL: PoolTemplate = PoolTemplate("Testing Token Pool", 0.5, 10, PaymentType.PPLNS_WINDOW,
    PoolInformation.TokenExchangeEmissions, PoolInformation.CURR_TEST_TOKENS, 5L, 10L)

  val templates: Array[PoolTemplate] = Array(STANDARD_POOL, SOLO_POOL, TEST_TOKEN_POOL)
}
