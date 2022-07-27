package io.getblok.subpooling_core
package cycles

import org.ergoplatform.appkit.ErgoId

class LPBoxNotFoundException(lpNFT: ErgoId) extends RuntimeException(s"Could not find LP box with NFT ${lpNFT}"){

}
