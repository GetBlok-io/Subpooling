package io.getblok.subpooling_core

import boxes.MetadataInputBox
import boxes.builders.MetadataOutputBuilder
import contracts.MetadataContract
import contracts.holding.SimpleHoldingContract

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract

package object contract_tests {
  val ergoClient: ErgoClient = RestApiErgoClient.create("http://188.34.207.91:9053/", NetworkType.MAINNET, "", "")
  val creatorAddress = Address.create("4MQyML64GnzMxZgm")
  val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"
  val dummyToken = "f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819"

  def buildUserBox(value: Long, index: Short): InputBox = {
    ergoClient.execute{
      ctx =>
        val inputBox = ctx.newTxBuilder().outBoxBuilder()
          .value(value)
          .contract(new ErgoTreeContract(creatorAddress.getErgoAddress.script, NetworkType.MAINNET))
          .build()
          .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", index)

        return inputBox
    }
  }

  def buildGenesisBox(value: Long, subpoolId: Long): MetadataInputBox = {
    ergoClient.execute{
      ctx =>
        val mOB = new MetadataOutputBuilder(ctx.newTxBuilder().outBoxBuilder())
        val metadataContract = MetadataContract.generateMetadataContract(ctx)
        val subpoolToken = ErgoId.create(dummyToken)
        val outBox = MetadataContract.buildGenesisBox(mOB, metadataContract, creatorAddress, value, ctx.getHeight, subpoolToken, subpoolId)
        new MetadataInputBox(outBox.convertToInputWith(dummyTxId, 0), subpoolToken)
    }
  }

  def buildHoldingBox(value: Long): InputBox = {
    ergoClient.execute{
      ctx =>
        val txB = ctx.newTxBuilder()
        val metadataContract = MetadataContract.generateMetadataContract(ctx)
        val subpoolToken = ErgoId.create(dummyToken)
        val holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, subpoolToken)
        val holdingBox = txB.outBoxBuilder()
          .value(value)
          .contract(holdingContract)
          .build()
          .convertToInputWith(dummyTxId, 0)

        holdingBox
    }
  }



  def dummyProver: ErgoProver = {
    ergoClient.execute{
      ctx =>
        val prover = ctx.newProverBuilder()
          .withDLogSecret(BigInt.apply(0).bigInteger)
          .build()

        return prover
    }
  }
}
