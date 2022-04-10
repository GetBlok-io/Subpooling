package io.getblok.subpooling_core
package groups.stages

import boxes.{CommandInputBox, CommandOutBox}
import groups.entities.{Pool, Subpool}

import io.getblok.subpooling_core.contracts.command.CommandContract
import io.getblok.subpooling_core.contracts.holding.HoldingContract
import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.groups.models.TransactionStage
import io.getblok.subpooling_core.transactions.CreateCommandTx
import org.ergoplatform.appkit.BlockchainContext

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try


class CommandStage(pool: Pool, ctx: BlockchainContext, wallet: NodeWallet, commandContract: CommandContract,
                   holdingContract: HoldingContract) extends TransactionStage[CommandInputBox](pool, ctx, wallet) {
  override val stageName = "CommandStage"

  override def executeStage: TransactionStage[CommandInputBox] = {
    result = {
      Try {
        var outputMap = Map.empty[Subpool, (CommandOutBox, Int)]
        var outputIndex = 0
        // Compile CommandBoxes using transaction
        for (subPool <- pool.subPools) {
          val createCommandTx = new CreateCommandTx(ctx.newTxBuilder())
          val inputs = List(subPool.rootBox)
          val nextHoldingInputs = List(subPool.holdingBox) ++ subPool.storedBox.toList

          val unsignedTx = createCommandTx
            .metadataToCopy(subPool.box)
            .withCommandContract(commandContract)
            .commandValue(AppParameters.commandValue)
            .inputBoxes(inputs: _*)
            .withHolding(holdingContract, nextHoldingInputs)
            .setDistribution(subPool.nextDist)
            .setPoolFees(subPool.nextFees)
            .setPoolInfo(subPool.nextInfo)
            .setPoolOps(subPool.nextOps)
            .buildCommandTx()

          outputMap = outputMap + (subPool -> (createCommandTx.commandOutBox, outputIndex))
          outputIndex = outputIndex + 1
        }
        // Execute global command tx
        val inputBoxes = pool.subPools.map(p => p.rootBox).toList
        val outputBoxes = outputMap.values.toArray.sortBy(o => o._2).map(o => o._1.asOutBox)
        val txFee = pool.subPools.size * AppParameters.groupFee

        val txB = ctx.newTxBuilder()
        val unsignedTx = txB
          .boxesToSpend(inputBoxes.asJava)
          .outputs(outputBoxes: _*)
          .fee(txFee)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()

        transaction = Try(wallet.prover.sign(unsignedTx))
        val txId = ctx.sendTransaction(transaction.get).replace("\"", "")

        val inputMap = outputMap.map(p => p._1 -> new CommandInputBox(p._2._1.convertToInputWith(txId, p._2._2.shortValue()), commandContract))

        inputMap
      }
    }

    this
  }


}
