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
    // Emissions
    private val emissionsSrc    = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/SimpleEmissions.ergo")
    private val emissionsTestSrc    = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/EmissionsTest.ergo")

    val METADATA_SCRIPT:          String = metadataSrc.mkString
    val METADATA_TEST_SCRIPT:     String = metadataTestSrc.mkString
    val SIMPLE_HOLDING_SCRIPT:    String = holdingSrc.mkString
    val TOKEN_HOLDING_SCRIPT:     String = tokenHoldSrc.mkString
    val SIMPLE_EMISSIONS_SCRIPT:  String = emissionsSrc.mkString
    val EMISSIONS_TEST_SCRIPT:    String = emissionsTestSrc.mkString
    metadataSrc.close()
    metadataTestSrc.close()
    holdingSrc.close()
    tokenHoldSrc.close()
    emissionsSrc.close()
    emissionsTestSrc.close()
  }
}
