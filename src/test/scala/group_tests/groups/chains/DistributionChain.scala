package group_tests.groups.chains

import app.AppParameters.NodeWallet
import boxes.MetadataInputBox
import contracts.holding.HoldingContract
import group_tests.groups.{entities, models}
import org.ergoplatform.appkit.BlockchainContext
import transactions.DistributionTx

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
