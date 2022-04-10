package io.getblok.subpooling_core
package group_tests.groups.chains

import group_tests.groups._
import group_tests.groups.models.TransactionChain

import org.ergoplatform.appkit.SignedTransaction


class ChainManager extends models.ManagerBase{
  override protected val managerName: String = "ChainManager"

  def execute[Output](chain: TransactionChain[Output]): (Map[entities.Subpool, (SignedTransaction, Output)], Map[entities.Subpool, Throwable]) = {
    val resultSet = chain.executeChain.resultSet

    val failures  = resultSet.filter(p => p._2.isFailure).map(p => p._1 -> p._2.failed.get)
    val success   = resultSet.filter(p => p._2.isSuccess).map(p => p._1 -> p._2.get)

    for(failed <- failures){
      logger.error(s"Error for subPool #${failed._1.id} during execution of chain ${chain.chainName}")
      logStacktrace(failed._2)
    }
    if(success.isEmpty)
      throw new ChainManagerException

    (success, failures)
  }
}
