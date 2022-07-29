package utils

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolInformation
import org.ergoplatform.appkit.{Address, ErgoId, NetworkType}

object EmissionTemplates {
  case class ExchangeTemplate(swapAddress: Address, feeAddress: Address, lpNFT: ErgoId, distToken: ErgoId, initFee: Long, initPercent: Long, totalEmissions: Long)

  // TODO: Add swap address
  // 1% shareOp Fee, 10% percent increase
  val NETA_MAINNET = ExchangeTemplate(Address.create("9gdLf3Zg1QHgH3BYjFrMA2DSm19CqPNKi9vTCeCT5NSmNZfV29T"), AppParameters.getFeeAddress, ErgoId.create("7d2e28431063cbb1e9e14468facc47b984d962532c19b0b14f74d0ce9ed459be"),
    ErgoId.create("472c3d4ecaa08fb7392ff041ee2e6af75f4a558810a74b28600549d5392810e8"), 3000, 8000, 5000000L*1000000)

  // Liquidity Pool Token Id, not LP NFT!
  // 4eab0718642b680f8bac258aec0c0b7edc5c2dc8bc0e7fac59103fb73947038f
  val NETA_TESTNET = ExchangeTemplate(Address.create("9gdLf3Zg1QHgH3BYjFrMA2DSm19CqPNKi9vTCeCT5NSmNZfV29T"), AppParameters.getFeeAddress, ErgoId.create("b8f583d242d6a9dc2b85f8346e1fdc3d512a2f0ae4740bf9d86ba57f5339b9d0"),
    ErgoId.create("1bad9a2df3c8a8fed18118e8338a50f2e61cb192d7fd868b149e17eef8dc8a62"), 3000, 8000, 5000000L*1000000)

  def getNETATemplate(networkType: NetworkType): ExchangeTemplate = {
    networkType match {
      case NetworkType.MAINNET =>
        NETA_MAINNET
      case NetworkType.TESTNET =>
        NETA_TESTNET
    }
  }

  case class ProportionalTemplate(swapAddress: Address, feeAddress: Address, distToken: ErgoId, initProportion: Long,
                                  initFee: Long, decimalPlaces: Long, totalEmissions: Long)

  val COMET_TESTNET = ProportionalTemplate(Address.create("9h6Ao31CVSsYisf4pWTM43jv6k3BaXV3jovGfaRj9PrqfYms6Rf"), AppParameters.getFeeAddress,
    ErgoId.create("24eb9230e766977811ef045184f24b1daf8b75cd4ca5c85880739449cae0085c"), 5, 1000, 1000000, 1000000000L)
  val COMET_MAINNET = ProportionalTemplate(Address.create("9h6Ao31CVSsYisf4pWTM43jv6k3BaXV3jovGfaRj9PrqfYms6Rf"), AppParameters.getFeeAddress,
    ErgoId.create("0cd8c9f416e5b1ca9f986a7f10a84191dfb85941619e49e53c0dc30ebf83324b"), 5, 1000, 1000000, 2000000000L)

  def getCOMETTemplate(networkType: NetworkType): ProportionalTemplate = {
    networkType match {
      case NetworkType.MAINNET =>
        COMET_MAINNET
      case NetworkType.TESTNET =>
        COMET_TESTNET
    }
  }

  case class HybridTemplate(swapAddress: Address, distToken: ErgoId, lpNFT: ErgoId, proportion: Long, percent: Long,
                            totalEmissions: Long)

  val ERGOPAD_MAINNET: HybridTemplate = HybridTemplate(
    Address.create("9frZjRM66Dn9eCbTfxKMT228M3j62QvFCpaXXWdfmmdmoV9Jdzh"),
    ErgoId.create("d71693c49a84fbbecd4908c94813b46514b18b67a99952dc1e6e4791556de413"),
    ErgoId.create("d7868533f26db1b1728c1f85c2326a3c0327b57ddab14e41a2b77a5d4c20f4b2"),
    10000L,
    10000L,
    500000L * 100L
  )

  def getErgoPadTemplate(networkType: NetworkType): HybridTemplate = {
    networkType match {
      case NetworkType.MAINNET => ERGOPAD_MAINNET
      case NetworkType.TESTNET => ERGOPAD_MAINNET // TODO: CHANGE LATER
    }
  }


}
