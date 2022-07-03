package io.getblok.subpooling_core
package contracts

import io.getblok.subpooling_core.global.AppParameters

import scala.io.Source
object Models {
  object Scripts {
    // Metadata
    private val metadataSrc     = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/Metadata.ergo")
    private val metadataTestSrc = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/MetadataTest.ergo")
    // Holding
    private val holdingSrc      = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/SubpoolHolding.ergo")
    private val tokenHoldSrc    = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/TokenHolding.ergo")
    private val additiveHoldSrc    = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/AdditiveHolding.ergo")
    // Emissions
    private val emissionsSrc    = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/SimpleEmissions.ergo")
    private val emissionsTestSrc    = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/EmissionsTest.ergo")
    private val exEmissionsSrc      = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/ExchangedEmissions.ergo")
    private val exEmissionsTestSrc      = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/ExchangedEmissionsTest.ergo")

    private val propEmissionsSrc      = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/ProportionalEmissions.ergo")
    private val propEmissionsTestSrc      = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/ProportionalEmissionsTest.ergo")

    private val shareStateSrc         = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/ShareState.ergo")

    val METADATA_SCRIPT:            String = metadataSrc.mkString
    val METADATA_TEST_SCRIPT:       String = metadataTestSrc.mkString
    val SIMPLE_HOLDING_SCRIPT:      String = holdingSrc.mkString
    val TOKEN_HOLDING_SCRIPT:       String = tokenHoldSrc.mkString
    val ADD_HOLDING_SCRIPT:         String = additiveHoldSrc.mkString
    val SIMPLE_EMISSIONS_SCRIPT:    String = emissionsSrc.mkString
    val EMISSIONS_TEST_SCRIPT:      String = emissionsTestSrc.mkString
    val EX_EMISSIONS_SCRIPT:        String = exEmissionsSrc.mkString
    val EX_EMISSIONS_TEST_SCRIPT:   String = exEmissionsTestSrc.mkString
    val PROP_EMISSIONS_SCRIPT:      String = propEmissionsSrc.mkString
    val PROP_EMISSIONS_TEST_SCRIPT: String = propEmissionsTestSrc.mkString
    val SHARE_STATE_SCRIPT:         String = shareStateSrc.mkString

    metadataSrc.close()
    metadataTestSrc.close()
    holdingSrc.close()
    tokenHoldSrc.close()
    additiveHoldSrc.close()
    emissionsSrc.close()
    emissionsTestSrc.close()
    exEmissionsSrc.close()
    exEmissionsTestSrc.close()
    propEmissionsSrc.close()
    propEmissionsTestSrc.close()
    shareStateSrc.close()
  }
}
