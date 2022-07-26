package io.getblok.subpooling_core
package cycles.models

import persistence.models.PersistenceModels.PoolPlacement

import org.ergoplatform.appkit.{ErgoId, InputBox, SignedTransaction}

case class CycleResults(nextCycleBox: InputBox, tx: SignedTransaction, placements: Seq[PoolPlacement], amountEmitted: Long, amountLeft: Long,
                        ergTaken: Long, ergRewarded: Long, emissionRate: Long, exchangeRate: Option[Long] = None) {

}
