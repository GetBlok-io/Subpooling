package io.getblok.subpooling_core
package groups.chains

import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.holding.HoldingContract
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.entities.Pool
import io.getblok.subpooling_core.groups.models.TransactionChain
import io.getblok.subpooling_core.transactions.DistributionTx
import org.ergoplatform.appkit.BlockchainContext

import scala.util.Try


class DistributionChain(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet,
                        holdingContract: HoldingContract) extends TransactionChain[MetadataInputBox](pool, ctx, wallet) {

  override val chainName: String = "DistributionChain"

  override def executeChain: TransactionChain[MetadataInputBox] = {

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
        val txId = ctx.sendTransaction(signedTx).replace("\"", "")
        subPool.nextBox = new MetadataInputBox(distTx.metadataOutBox.convertToInputWith(txId, 0), subPool.token)
        signedTx -> subPool.nextBox
      }

    }.toMap

    this
  }
}
