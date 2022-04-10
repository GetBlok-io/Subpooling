package io.getblok.subpooling_core

import boxes.MetadataInputBox
import boxes.builders.MetadataOutputBuilder
import contracts.MetadataContract
import contracts.holding.SimpleHoldingContract
import group_tests.MockData.{creatorAddress, dummyToken, dummyTxId, ergoClient}
import group_tests.groups.entities.{Member, Pool, Subpool}

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

package object group_tests {

  def logger: Logger = LoggerFactory.getLogger("GroupTesting")

  def buildUserBox(value: Long): InputBox = {
    ergoClient.execute{
      ctx =>
        val inputBox = ctx.newTxBuilder().outBoxBuilder()
          .value(value)
          .contract(new ErgoTreeContract(creatorAddress.getErgoAddress.script, NetworkType.MAINNET))
          .build()
          .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)

        return inputBox
    }
  }

  def buildGenesisBox(value: Long, subpoolId: Long): MetadataInputBox = {
    ergoClient.execute{
      ctx =>
        val mOB = new MetadataOutputBuilder(ctx.newTxBuilder().outBoxBuilder())
        val metadataContract = MetadataContract.generateTestContract(ctx)
        val subpoolToken = ErgoId.create(dummyToken)
        val outBox = MetadataContract.buildGenesisBox(mOB, metadataContract, creatorAddress, value, ctx.getHeight, subpoolToken, subpoolId)
        new MetadataInputBox(outBox.convertToInputWith(dummyTxId, 0), subpoolToken)
    }
  }

  def buildHoldingBox(value: Long): InputBox = {
    ergoClient.execute{
      ctx =>
        val txB = ctx.newTxBuilder()
        val metadataContract = MetadataContract.generateTestContract(ctx)
        val subpoolToken = ErgoId.create(dummyToken)
        val holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataContract.getAddress, subpoolToken)
        val holdingBox = txB.outBoxBuilder()
          .value(value)
          .contract(holdingContract.asErgoContract)
          .build()
          .convertToInputWith(dummyTxId, 0)

        holdingBox
    }
  }

  def getInputBoxes: Array[InputBox] = Array(buildUserBox(Parameters.OneErg * 122))

  def dummyProver: ErgoProver = {
    ergoClient.execute{
      ctx =>
        val prover = ctx.newProverBuilder()
          .withDLogSecret(BigInt.apply(0).bigInteger)
          .build()

        return prover
    }
  }




  def initSinglePool: Pool = {
    val subPool = buildGenesisBox(Parameters.OneErg, 0)
    new Pool(ArrayBuffer(new Subpool(subPool)))
  }

  def randomShareScore: Long = {
    Random.nextInt(500000).toLong
  }

  def randomMinPay: Long = {
    Random.nextInt(10000) * Parameters.MinFee + (Parameters.MinFee * 10) + Parameters.OneErg * 15
  }

  def printMembers(members: Array[Member]): Unit = {
    logger.info("======Printing Members======")
    members.foreach(m => logger.info(m.toDistributionValue.toString()))
  }


}
