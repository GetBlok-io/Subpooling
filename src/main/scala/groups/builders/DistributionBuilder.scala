package groups.builders

import app.AppParameters
import app.AppParameters.NodeWallet
import boxes.MetadataInputBox
import contracts.command.CommandContract
import groups.entities.{Pool, Subpool}
import groups.models.GroupBuilder
import org.ergoplatform.appkit.{BlockchainContext, InputBox, OutBox}

class DistributionBuilder(holdingMap: Map[MetadataInputBox, InputBox], storageMap: Map[MetadataInputBox, InputBox],
                          commandContract: CommandContract) extends GroupBuilder{

  /**
   * Collect information that already exists about this Transaction Group and assign it to each subPool
   */
  override def collectGroupInfo: GroupBuilder = {
    for(subPool <- pool.subPools){
      subPool.holdingBox = holdingMap.find(b => b._1.getId == subPool.box.getId).get._2
      subPool.storedBox = storageMap.find(b => b._1.getId == subPool.box.getId).map(o => o._2)
    }
    this
  }

  /**
   * Apply special modifications to the entire Transaction Group
   */
  override def applyModifications: GroupBuilder = {
    pool.subPools.foreach{
      s =>
        s.nextFees = s.box.poolFees
        s.nextInfo = s.box.poolInfo
        s.nextOps  = s.box.poolOps
    }
    this
  }

  /**
   * Execute the root transaction necessary to begin the Group's Tx Chains
   *
   * @param ctx Blockchain Context to execute root transaction in
   * @return this Group Builder, with it's subPools assigned to the correct root boxes.
   */
  override def executeRootTx(ctx: BlockchainContext, wallet: NodeWallet): GroupBuilder = {
    val totalFees    = pool.subPools.size * AppParameters.groupFee
    val totalOutputs = pool.subPools.size * AppParameters.commandValue

    // TODO: Possibly use subpool id if reference issues arise
    var outputMap    = Map.empty[Subpool, (OutBox, Int)]
    var outputIndex: Int = 0
    for(subPool <- pool.subPools){

      val outB = ctx.newTxBuilder().outBoxBuilder()

      val outBox = outB
        .contract(wallet.contract)
        .value(AppParameters.commandValue)
        .build()

      outputMap = outputMap + (subPool -> (outBox -> outputIndex))
      outputIndex = outputIndex + 1
    }

    val inputBoxes = ctx.getWallet.getUnspentBoxes(totalFees + totalOutputs)
    val txB = ctx.newTxBuilder()

    val unsignedTx = txB
      .boxesToSpend(inputBoxes.get())
      .fee(totalFees)
      .outputs(outputMap.values.toSeq.sortBy(o => o._2).map(o => o._1):_*)
      .sendChangeTo(wallet.p2pk.getErgoAddress)
      .build()

    val signedTx = wallet.prover.sign(unsignedTx)
    val txId = ctx.sendTransaction(signedTx)

    val inputMap: Map[Subpool, InputBox] = outputMap.map(om => om._1 -> om._2._1.convertToInputWith(txId, om._2._2.shortValue()))
    pool.subPools.foreach{
      p =>
        p.rootBox = inputMap(p)
    }

    this
  }

  /**
   * Finalize building of the Transaction Group
   */
  override def buildGroup: Pool = {
    pool
  }
}
