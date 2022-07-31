package io.getblok.subpooling_core
package cycles.models

import persistence.models.PersistenceModels.PoolPlacement

import io.getblok.subpooling_core.global.Helpers
import org.ergoplatform.appkit.{InputBox, SignedTransaction}

case class EmissionResults(lpBox: InputBox,
                           amountEmitted: Long, ergTaken: Long, ergRewarded: Long, feeTaken: Long,
                           emissionRate: Double, exchangeRate: Option[Double] = None) {
  override def toString: String = {
    "\n====================================="+
    "\nEMISSION RESULTS:"+
    s"\nAMOUNT EMITTED: ${amountEmitted / 100.0}" +
    s"\nEMISSION RATE: ${emissionRate / 100.0}" +
    s"\nERG TAKEN: ${Helpers.nanoErgToErg(ergTaken)}" +
    s"\nERG REWARDED: ${Helpers.nanoErgToErg(ergRewarded)}" +
    s"\nFEE TAKEN: ${Helpers.nanoErgToErg(feeTaken)}"+
    s"\nEXCHANGE RATE: ${exchangeRate.map(_ / 100.0)}" +
    s"\n====================================="
  }
}
