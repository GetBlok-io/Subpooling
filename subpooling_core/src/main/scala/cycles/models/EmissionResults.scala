package io.getblok.subpooling_core
package cycles.models

import persistence.models.PersistenceModels.PoolPlacement

import io.getblok.subpooling_core.global.Helpers
import org.ergoplatform.appkit.{InputBox, SignedTransaction}

case class EmissionResults(lpBox: Option[InputBox], amountEmitted: Long, ergTaken: Long, ergRewarded: Long, feeTaken: Long,
                           emissionRate: Double, exchangeRate: Option[Double] = None) {
  override def toString: String = {
    "\n====================================="+
    "\nEMISSION RESULTS:"+
    s"\nAMOUNT EMITTED: ${amountEmitted}" +
    s"\nEMISSION RATE: ${emissionRate}" +
    s"\nERG TAKEN: ${Helpers.nanoErgToErg(ergTaken)}" +
    s"\nERG REWARDED: ${Helpers.nanoErgToErg(ergRewarded)}" +
    s"\nFEE TAKEN: ${Helpers.nanoErgToErg(feeTaken)}"+
    s"\nEXCHANGE RATE: ${exchangeRate}" +
    s"\n====================================="
  }
}
