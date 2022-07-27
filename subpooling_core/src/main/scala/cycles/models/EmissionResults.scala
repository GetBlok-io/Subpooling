package io.getblok.subpooling_core
package cycles.models

import persistence.models.PersistenceModels.PoolPlacement

import org.ergoplatform.appkit.{InputBox, SignedTransaction}

case class EmissionResults(amountEmitted: Long, ergTaken: Long, ergRewarded: Long,
                           emissionRate: Double, exchangeRate: Option[Double] = None) {
  override def toString: String = {
    "====================================="+
    "\nEMISSION RESULTS:"+
    s"\nAMOUNT EMITTED: ${amountEmitted}" +
    s"\nEMISSION RATE: ${emissionRate}" +
    s"\nERG TAKEN: ${ergTaken}" +
    s"\nERG REWARDED: ${ergRewarded}" +
    s"\nEXCHANGE RATE: ${exchangeRate}" +
    s"====================================="
  }
}
