package contracts

import scala.io.Source

object Models {
  object Scripts {
    private val metadataSrc     = Source.fromFile("src/main/resources/scripts/Metadata.ergo")
    private val metadataTestSrc = Source.fromFile("src/main/resources/scripts/MetadataTest.ergo")
    private val holdingSrc      = Source.fromFile("src/main/resources/scripts/SimpleHolding.ergo")

    val METADATA_SCRIPT:        String = metadataSrc.mkString
    val METADATA_TEST_SCRIPT:   String = metadataTestSrc.mkString
    val SIMPLE_HOLDING_SCRIPT:  String = holdingSrc.mkString

    metadataSrc.close()
    metadataTestSrc.close()
    holdingSrc.close()
  }
}
