package io.getblok.subpooling_core
package contracts.plasma
import contracts.MetadataContract
import contracts.Models.Scripts

import org.ergoplatform.appkit.{BlockchainContext, Constants, ConstantsBuilder, ErgoContract, ErgoType, ErgoValue, OutBox}
import sigmastate.Values
import io.getblok.getblok_plasma.collections.{PlasmaMap, Proof}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.{PartialStateMiner, ShareState, StateMiner, StateScore}
import sigmastate.eval.Colls


case class ShareStateContract(contract: ErgoContract){
  import ShareStateContract._
  def getConstants:                             Constants       = contract.getConstants
  def getErgoScript:                            String          = script
  def substConstant(name: String, value: Any):  ErgoContract    = contract.substConstant(name, value)
  def getErgoTree:                              Values.ErgoTree = contract.getErgoTree
}

object ShareStateContract {
  private val constants = ConstantsBuilder.create().build()
  val script: String = Scripts.SHARE_STATE_SCRIPT

  def generateStateContract(ctx: BlockchainContext): ErgoContract = {
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  def buildStateBox(ctx: BlockchainContext, shareState: ShareState, maxScore: Int): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(Helpers.MinFee)
      .registers(shareState.map.ergoValue, ErgoValue.of(maxScore))
      .contract(generateStateContract(ctx))
      .build()
  }

  def buildRewardBox(ctx: BlockchainContext, value: Long, initReward: Long, contract: ErgoContract): OutBox = {
    ctx.newTxBuilder().outBoxBuilder()
      .value(value)
      .registers(ErgoValue.of(initReward))
      .contract(contract)
      .build()
  }

  def buildDataBoxes(ctx: BlockchainContext, shareState: ShareState, updates: Seq[(PartialStateMiner, StateScore)], contract: ErgoContract): Seq[OutBox] = {
    val updateType = ErgoType.pairType(ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))
    val updateBox = ctx.newTxBuilder().outBoxBuilder()
      .value(Helpers.MinFee)
      .registers(ErgoValue.of(Colls.fromArray(updates.map(u => u._1 -> u._2.copy(paid = true)).toArray.map(u => u._1.toColl -> u._2.toColl))(updateType.getRType), updateType))
      .contract(contract)
      .build()

    val proof = shareState.map.update(updates.map(u => u._1 -> u._2.copy(paid = true)):_*).proof

    val proofs = {
      if(proof.bytes.length > 3800){
        val fullProofShards = proof.bytes.length / 3800
        val partialShard = proof.bytes.length % 3800

        var shards = for(i <- 0 until fullProofShards) yield Proof(proof.bytes.slice(i * 3800, ((i + 1) * 3800)))
        if(partialShard > 0)
          shards = shards ++ Seq(Proof(proof.bytes.slice(proof.bytes.length - partialShard, proof.bytes.length)))
        shards
      }else
        Seq(proof)
    }
    val proofBoxes = for(p <- proofs) yield {
      ctx.newTxBuilder().outBoxBuilder()
        .value(Helpers.MinFee)
        .registers(p.ergoValue)
        .contract(contract)
        .build()
    }

    Seq(updateBox) ++ proofBoxes
  }

  def buildPaymentBoxes(ctx: BlockchainContext, updates: Seq[(StateMiner, StateScore)], blockReward: Long, maxScore: Int): Seq[OutBox] = {
    for(u <- updates) yield {
      ctx.newTxBuilder().outBoxBuilder()
        .value( (u._2.score * blockReward) / maxScore)
        .contract(u._1.address.toErgoContract)
        .build()
    }
  }
}

