package io.getblok.subpooling_core
package cycles.models

import persistence.models.PersistenceModels.PoolPlacement

import org.ergoplatform.appkit.{ErgoId, InputBox, SignedTransaction}

case class CycleResults(nextCycleBox: InputBox, nextHoldingBox: InputBox, emTx: SignedTransaction, setupTx: SignedTransaction,
                        emissionResults: EmissionResults, amountLeft: Long) {

}
