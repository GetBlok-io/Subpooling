package io.getblok.subpooling_core
package cycles

import org.ergoplatform.appkit.ErgoId

class EmissionsBoxNotFoundException(emNFT: ErgoId) extends RuntimeException(s"Could not find emissions box with NFT ${emNFT}"){

}
