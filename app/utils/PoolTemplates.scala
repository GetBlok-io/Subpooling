package utils

import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts
import io.getblok.subpooling_core.contracts.plasma.PlasmaScripts.ScriptType
import io.getblok.subpooling_core.payments.Models.PaymentType
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolInformation
import org.ergoplatform.appkit.Address

object PoolTemplates {
  case class PoolTemplate(title: String, fee: Double, numSubpools: Int, paymentType: PaymentType, emissionsType: String,
                          currency: String, epochKick: Long, maxMembers: Long, tokenName: String, tokenDesc: String,
                          feeOp: Option[Address] = None, scriptType: Option[ScriptType] = None)

  case class UninitializedPool(poolMade: Boolean, emissionsMade: Option[Boolean], template: PoolTemplate, isPlasma: Boolean = false)
  val STANDARD_POOL: PoolTemplate = PoolTemplate("GetBlok.io Smart Pool", 0.005, 100, PaymentType.PPLNS_WINDOW,
    PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 5L, 10L,
    "GetBlok.io Default Smart Pool", "This token represents the default Smart Pool on GetBlok.io",
    Some(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q")))
  val SOLO_POOL: PoolTemplate = PoolTemplate("GetBlok.io Smart Pool SOLO", 0.01, 100,PaymentType.PLASMA_SOLO_BATCH,
    PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 10L, 10L,
    "GetBlok.io SOLO Pool", "Identification token for GetBlok.io's smart contract based SOLO pool",
    Some(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q")))
  val TEST_TOKEN_POOL: PoolTemplate = PoolTemplate("Testing Token Pool", 0.005, 10, PaymentType.PPLNS_WINDOW,
    PoolInformation.TokenExchangeEmissions, PoolInformation.CURR_TEST_TOKENS, 3L, 5L,
    "GetBlok.io Token Test Pool",
    "GetBlok.io Test Token Pool identification token")

//  val OLD_NETA_POOL: PoolTemplate = PoolTemplate("anetaBTC Smart Pool", 0, 100, PaymentType.PPLNS_WINDOW,
//    PoolInformation.TokenExchangeEmissions, PoolInformation.CURR_NETA, 5L, 10L,
//    "anetaBTC Smart Pool",
//    "anetaBTC Smart Pool identification token",
//    Some(Address.create("9gdLf3Zg1QHgH3BYjFrMA2DSm19CqPNKi9vTCeCT5NSmNZfV29T")))
  val COMET_POOL: PoolTemplate = PoolTemplate("COMET Smart Pool", 0.01, 100, PaymentType.PPLNS_WINDOW,
    PoolInformation.ProportionalEmissions, PoolInformation.CURR_ERG_COMET, 5L, 10L,
    "COMET Smart Pool",
    "COMET Smart Pool identification token",
    Some(Address.create("9h6Ao31CVSsYisf4pWTM43jv6k3BaXV3jovGfaRj9PrqfYms6Rf")))
  val PLASMA_STD_POOL: PoolTemplate = PoolTemplate("GetBlok.io Default Plasma Pool", 0.01, 1, PaymentType.PLASMA_PPLNS_WINDOW,
    PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 5L, 10L,
    "GetBlok.io Default Plasma Pool", "This token represents the default Plasma Pool on GetBlok.io",
    Some(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q")),
    Some(PlasmaScripts.SINGLE)
  )

  val SOLO_PLASMA_POOL: PoolTemplate = PoolTemplate("GetBlok.io SOLO Plasma Pool", 0.02, 1, PaymentType.PLASMA_SOLO_BATCH,
    PoolInformation.NoEmissions, PoolInformation.CURR_ERG, 10L, 1000L,
    "GetBlok.io SOLO Plasma Pool", "This token represents the SOLO Plasma Pool on GetBlok.io",
    Some(Address.create("9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q")),
    Some(PlasmaScripts.SINGLE)
  )
  val NETA_PLASMA_POOL: PoolTemplate = PoolTemplate("anetaBTC Phase 3 Smart Pool", 0.03, 1, PaymentType.PLASMA_PPLNS_WINDOW,
    PoolInformation.NFTExchangeEmissions, PoolInformation.CURR_NETA, 5L, 10L,
    "anetaBTC Phase 3 Smart Pool",
    "anetaBTC Smart Pool identification token",
    Some(Address.create("9gdLf3Zg1QHgH3BYjFrMA2DSm19CqPNKi9vTCeCT5NSmNZfV29T")),
    Some(PlasmaScripts.SINGLE_TOKEN))

  val ERGOPAD_POOL: PoolTemplate = PoolTemplate("Ergopad Smart Pool", 0.03, 1, PaymentType.PLASMA_PPLNS_WINDOW,
    PoolInformation.HybridExchangeEmissions, PoolInformation.CURR_ERG_ERGOPAD, 10L, 1000L,
    "Ergopad Smart Pool", "This token represents the Ergopad Smart Pool on GetBlok.io",
    Some(Address.create("9frZjRM66Dn9eCbTfxKMT228M3j62QvFCpaXXWdfmmdmoV9Jdzh")),
    Some(PlasmaScripts.DUAL)
  )

  val FLUX_POOL: PoolTemplate = PoolTemplate("Flux Smart Pool", 0.03, 1, PaymentType.PLASMA_PPLNS_WINDOW,
    PoolInformation.HybridNormalExchangeEmissions, PoolInformation.CURR_ERG_FLUX, 10L, 10000L,
    "Flux Smart Pool", "This token represents the Flux Smart Pool on GetBlok.io",
    Some(Address.create("9frZjRM66Dn9eCbTfxKMT228M3j62QvFCpaXXWdfmmdmoV9Jdzh")),
    Some(PlasmaScripts.DUAL)
  )

  val templates: Array[UninitializedPool] = Array(
    UninitializedPool(poolMade = false, Some(false), NETA_PLASMA_POOL, isPlasma = true),
    //UninitializedPool(poolMade = false, Some(false), OLD_NETA_POOL),
    UninitializedPool(poolMade = false, None, PLASMA_STD_POOL, isPlasma = true),
    UninitializedPool(poolMade = false, None, SOLO_PLASMA_POOL, isPlasma = true),
    UninitializedPool(poolMade = false, Some(false), ERGOPAD_POOL, isPlasma = true),
    UninitializedPool(poolMade = false, Some(false), FLUX_POOL, isPlasma = true),
//    UninitializedPool(poolMade = false, None, STANDARD_POOL),
//    UninitializedPool(poolMade = false, None, SOLO_POOL),
//    UninitializedPool(poolMade = false, Some(false), COMET_POOL),
//    ,

    )

  def getPaymentStr(paymentType: PaymentType): String = {
    paymentType match {
      case PaymentType.PPLNS_WINDOW =>
        PoolInformation.PAY_PPLNS
      case PaymentType.SOLO_SHARES =>
        PoolInformation.PAY_SOLO
      case PaymentType.EQUAL_PAY =>
        PoolInformation.PAY_EQ
      case PaymentType.PLASMA_PPLNS_WINDOW =>
        PoolInformation.PAY_PLASMA_PPLNS
      case PaymentType.PLASMA_SOLO_BATCH =>
        PoolInformation.PAY_PLASMA_SOLO
      case _ =>
        PoolInformation.PAY_PPLNS
    }
  }
}
