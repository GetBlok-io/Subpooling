package utils

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolInformation
import org.ergoplatform.appkit.{Address, ErgoId, NetworkType}

object EmissionTemplates {
  case class ExchangeTemplate(swapAddress: Address, feeAddress: Address, lpNFT: ErgoId, distToken: ErgoId, initFee: Long, initPercent: Long, totalEmissions: Long)

  // TODO: Add swap address
  // 1% shareOp Fee, 10% percent increase
  val NETA_MAINNET = ExchangeTemplate(Address.create("9gdLf3Zg1QHgH3BYjFrMA2DSm19CqPNKi9vTCeCT5NSmNZfV29T"), AppParameters.getFeeAddress, ErgoId.create("7d2e28431063cbb1e9e14468facc47b984d962532c19b0b14f74d0ce9ed459be"),
    ErgoId.create("472c3d4ecaa08fb7392ff041ee2e6af75f4a558810a74b28600549d5392810e8"), 3000, 3000, 5000000L*1000000)

  // Liquidity Pool Token Id, not LP NFT!
  // b6a27a74d7bf868026a842871f84b3ee2b02a9e7d61d879ce221c78dc6865f83
  val NETA_TESTNET = ExchangeTemplate(Address.create("9gdLf3Zg1QHgH3BYjFrMA2DSm19CqPNKi9vTCeCT5NSmNZfV29T"), AppParameters.getFeeAddress, ErgoId.create("6ae6d30ca34fdbae266324321ea0eff0e9b6867a6f3544d86b21e6265ea9d7a8"),
    ErgoId.create("1c9dd7d85d162b6b19f88b3bf259f4f0f815b5c19562fd0777a7ff4a2642e4e8"), 3000, 3000, 5000000L*1000000)

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

  val ERGOPAD_TESTNET: HybridTemplate = HybridTemplate(
    Address.create("9frZjRM66Dn9eCbTfxKMT228M3j62QvFCpaXXWdfmmdmoV9Jdzh"),
    ErgoId.create("9bddf35f76aedb8409029c661024759c2ad5bdafc7e8784649354529c2bf5cde"),
    ErgoId.create("544e2fe15462c84f6fd149422832da0b363abc03a831f4f4a8685c2479891d97"),
    10000L,
    10000L,
    500000L * 100L
  )

  def getErgoPadTemplate(networkType: NetworkType): HybridTemplate = {
    networkType match {
      case NetworkType.MAINNET => ERGOPAD_MAINNET
      case NetworkType.TESTNET => ERGOPAD_TESTNET // TODO: CHANGE LATER
    }
  }


}
