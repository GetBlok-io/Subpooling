package groups.chains

import app.AppParameters
import app.AppParameters.NodeWallet
import boxes.{CommandInputBox, CommandOutBox, MetadataInputBox}
import contracts.command.CommandContract
import contracts.holding.HoldingContract
import groups.entities.{Pool, Subpool}
import groups.models.TransactionChain
import org.ergoplatform.appkit.BlockchainContext
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try


class DistributionChain(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet,
                        holdingContract: HoldingContract) extends TransactionChain[MetadataInputBox](pool, ctx, wallet){

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
        val txId = ctx.sendTransaction(signedTx)
        subPool.nextBox = new MetadataInputBox(distTx.metadataOutBox.convertToInputWith(txId, 0), subPool.token)
        signedTx -> subPool.nextBox
      }

    }.toMap

    this
  }
}
