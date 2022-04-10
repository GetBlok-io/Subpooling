package io.getblok.subpooling_core
package groups.stages

import groups.entities.{Pool, Subpool}

import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.models.TransactionStage
import io.getblok.subpooling_core.transactions.GenerateMultipleTx
import org.ergoplatform.appkit.BlockchainContext

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.util.Try

class GenesisStage(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, metadataVal: Long)
  extends TransactionStage[MetadataInputBox](pool, ctx, wallet) {
  override val stageName: String = "GenesisStage"

  override def executeStage: TransactionStage[MetadataInputBox] = {

    result = {
      Try {
        val contract = MetadataContract.generateMetadataContract(ctx)
        val poolToken = pool.rootTx.getOutputsToSpend.get(0).getTokens.get(0)
        val genesisTx = new GenerateMultipleTx(ctx.newTxBuilder())
        val unsignedTx = genesisTx
          .txFee(AppParameters.groupFee)
          .creatorAddress(wallet.p2pk)
          .metadataContract(contract)
          .metadataValue(metadataVal)
          .tokenInputBox(pool.rootTx.getOutputsToSpend.get(0))
          .smartPoolToken(poolToken)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = ctx.sendTransaction(transaction.get)

        val subPools = for (metadataBox <- transaction.get.getOutputsToSpend.asScala.filter(i => i.getErgoTree.bytes sameElements contract.getErgoTree.bytes))
          yield new Subpool(new MetadataInputBox(metadataBox, poolToken.getId))

        val poolMap = subPools.map(p => p -> p.box).toMap
        poolMap
      }
    }

    this
  }

}
