package io.getblok.subpooling
package group_tests.groups.chains

import group_tests.groups.{entities, models}
import global.AppParameters.NodeWallet

import core.boxes.MetadataInputBox
import core.contracts.holding.HoldingContract
import core.transactions.DistributionTx
import org.ergoplatform.appkit.BlockchainContext


import scala.util.Try


class DistributionChain(pool: entities.Pool, ctx: BlockchainContext, wallet: NodeWallet,
                        holdingContract: HoldingContract) extends models.TransactionChain[MetadataInputBox](pool, ctx, wallet){

  override val chainName: String = "DistributionChain"

  override def executeChain: models.TransactionChain[MetadataInputBox] = {

    resultSet = {

      for (subPool <- pool.subPools) yield subPool -> Try {
        val holdingInputs = List(subPool.holdingBox) ++ subPool.storedBox.toList
        val distTx = new DistributionTx(ctx.newTxBuilder())

        val unsignedTx = distTx
          .metadataInput(subPool.box)
          .commandInput(subPool.commandBox)
          .holdingInputs(holdingInputs)
          .holdingContract(holdingContract)
          .operatorAddress(wallet.p2pk)
          .buildMetadataTx()

        val signedTx = wallet.prover.sign(unsignedTx)
        // val txId = ctx.sendTransaction(signedTx)
        subPool.nextBox = new MetadataInputBox(distTx.metadataOutBox.convertToInputWith(signedTx.getId, 0), subPool.token)
        signedTx -> subPool.nextBox
      }

    }.toMap

    this
  }
}
