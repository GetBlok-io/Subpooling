package io.getblok.subpooling_core
package groups.chains

import groups.entities.Subpool
import groups.models.{ManagerBase, TransactionChain}

import org.ergoplatform.appkit.SignedTransaction

class ChainManager extends ManagerBase {
  override protected val managerName: String = "ChainManager"

  def execute[Output](chain: TransactionChain[Output]): (Map[Subpool, (SignedTransaction, Output)], Map[Subpool, Throwable]) = {
    val resultSet = chain.executeChain.resultSet

    val failures = resultSet.filter(p => p._2.isFailure).map(p => p._1 -> p._2.failed.get)
    val success = resultSet.filter(p => p._2.isSuccess).map(p => p._1 -> p._2.get)

    if (success.isEmpty)
      throw new ChainManagerException
    else {
      for (failed <- failures) {
        logger.error(s"Error for subPool #${failed._1.id} during execution of chain ${chain.chainName}")
        logStacktrace(failed._2)
      }
    }

    (success, failures)
  }
}
