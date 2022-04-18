package io.getblok.subpooling_core
package contracts

import io.getblok.subpooling_core.global.AppParameters

import scala.io.Source
object Models {
  object Scripts {

    private val metadataSrc     = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/Metadata.ergo")
    private val metadataTestSrc = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/MetadataTest.ergo")
    private val holdingSrc      = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/SubpoolHolding.ergo")
    private val tokenHoldSrc    = Source.fromFile(AppParameters.scriptBasePath + "conf/scripts/TokenHolding.ergo")
    val METADATA_SCRIPT:        String = metadataSrc.mkString
    val METADATA_TEST_SCRIPT:   String = metadataTestSrc.mkString
    val SIMPLE_HOLDING_SCRIPT:  String = holdingSrc.mkString
    val TOKEN_HOLDING_SCRIPT:   String = tokenHoldSrc.mkString
    metadataSrc.close()
    metadataTestSrc.close()
    holdingSrc.close()
    tokenHoldSrc.close()
  }
}
