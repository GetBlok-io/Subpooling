package utils

import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.Models.PoolInformation
import org.ergoplatform.appkit.Address

object PoolTemplates {
  case class PoolTemplate(title: String, fee: Double, numSubpools: Int, paymentType: PaymentType, emissionsType: String,
                          currency: String, epochKick: Long, maxMembers: Long, tokenName: String, tokenDesc: String,
                          feeOp: Option[Address] = None)

  case class UninitializedPool(poolMade: Boolean, emissionsMade: Option[Boolean], template: PoolTemplate)
  val STANDARD_POOL: PoolTemplate = PoolTemplate("GetBlok.io Smart Pool", 0.005, 100, PaymentType.PPLNS_WINDOW,
    PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 5L, 10L,
    "GetBlok.io Default Smart Pool", "This token represents the default Smart Pool on GetBlok.io",
    Some(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q")))
  val SOLO_POOL: PoolTemplate = PoolTemplate("GetBlok.io Smart Pool SOLO", 0.01, 100,PaymentType.SOLO_SHARES,
    PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 10L, 10L,
    "GetBlok.io SOLO Pool", "Identification token for GetBlok.io's smart contract based SOLO pool",
    Some(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q")))
  val TEST_TOKEN_POOL: PoolTemplate = PoolTemplate("Testing Token Pool", 0.005, 10, PaymentType.PPLNS_WINDOW,
    PoolInformation.TokenExchangeEmissions, PoolInformation.CURR_TEST_TOKENS, 3L, 5L,
    "GetBlok.io Token Test Pool",
    "GetBlok.io Test Token Pool identification token")
  val NETA_POOL: PoolTemplate = PoolTemplate("anetaBTC Smart Pool", 0.005, 100, PaymentType.PPLNS_WINDOW,
    PoolInformation.TokenExchangeEmissions, PoolInformation.CURR_NETA, 5L, 10L,
    "anetaBTC Smart Pool",
    "anetaBTC Smart Pool identification token")
  val COMET_POOL: PoolTemplate = PoolTemplate("COMET Smart Pool", 0.01, 100, PaymentType.PPLNS_WINDOW,
    PoolInformation.ProportionalEmissions, PoolInformation.CURR_ERG_COMET, 5L, 10L,
    "COMET Smart Pool",
    "COMET Smart Pool identification token",
    Some(Address.create("9h6Ao31CVSsYisf4pWTM43jv6k3BaXV3jovGfaRj9PrqfYms6Rf")))


  val templates: Array[UninitializedPool] = Array(
    UninitializedPool(poolMade = false, None, STANDARD_POOL),
    UninitializedPool(poolMade = false, None, SOLO_POOL),

    //UninitializedPool(poolMade = false, Some(false), NETA_POOL),
    UninitializedPool(poolMade = false, Some(false), COMET_POOL)
    )

  def getPaymentStr(paymentType: PaymentType): String = {
    paymentType match {
      case PaymentType.PPLNS_WINDOW =>
        PoolInformation.PAY_PPLNS
      case PaymentType.SOLO_SHARES =>
        PoolInformation.PAY_SOLO
      case PaymentType.EQUAL_PAY =>
        PoolInformation.PAY_EQ
      case _ =>
        PoolInformation.PAY_PPLNS
    }
  }
}
