package io.getblok.subpooling_core
package groups.stages.roots

import global.AppParameters
import global.AppParameters.NodeWallet
import groups.entities.{Pool, Subpool}
import groups.models.TransactionStage
import transactions.CreateSubpoolTokenTx

import org.ergoplatform.appkit.{BlockchainContext, InputBox}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.util.Try

class GenesisRoot(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, numSubpools: Long, metadataVal: Long)
  extends TransactionStage[InputBox](pool, ctx, wallet) {
  override val stageName: String = "GenesisRoot"

  override def executeStage: TransactionStage[InputBox] = {

    result = {
      Try {
        val totalValue = numSubpools * metadataVal + (AppParameters.groupFee * 2)
        val createSubpoolTokenTx = new CreateSubpoolTokenTx(ctx.newTxBuilder())
        val boxes: Seq[InputBox] = ctx.getWallet.getUnspentBoxes(totalValue).get().asScala.toSeq

        val unsignedTx = createSubpoolTokenTx
          .numSubpools(numSubpools)
          .metadataValue(metadataVal)
          .txFee(AppParameters.groupFee)
          .creatorAddress(wallet.p2pk)
          .inputBoxes(boxes)
          .build()


        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = ctx.sendTransaction(transaction.get)

        Map.empty[Subpool, InputBox]
      }
    }

    this
  }


}
