
import io.getblok.subpooling_core.contracts.emissions.EmissionsContract
import group_test.MockData._
import io.getblok.subpooling_core.boxes.{EmissionsBox, MetadataInputBox}
import io.getblok.subpooling_core.boxes.builders.MetadataOutputBuilder
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.holding.{HoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.groups.entities.{Member, Pool, Subpool}
import io.getblok.subpooling_core.registers.{LongReg, PropBytes}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

package object group_test {

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
        val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, subpoolToken)
        val holdingBox = txB.outBoxBuilder()
          .value(Parameters.MinFee * 10)
          .contract(holdingContract.asErgoContract)
          .tokens(new ErgoToken(dummyDistributionToken, value))
          .build()
          .convertToInputWith(dummyTxId, 0)

        holdingBox
    }
  }

  def buildEmissionsBox(tokenValue: Long, emissionReward: Long, exchangeAddress: Address, holdingContract: HoldingContract): EmissionsBox = {
    ergoClient.execute{
      ctx =>
        val txB = ctx.newTxBuilder()
        val emissionsContract = EmissionsContract.generate(ctx, shareOperator, emissionsOperator, holdingContract, isTest = true)
        val emissionsBox = txB.outBoxBuilder()
          .value(Parameters.MinFee)
          .contract(emissionsContract.contract)
          .tokens(new ErgoToken(dummyEmissionsToken, 1), new ErgoToken(dummyDistributionToken, tokenValue))
          .registers(LongReg(emissionReward).ergoVal, PropBytes.ofAddress(exchangeAddress)(ctx.getNetworkType).ergoVal)
          .build()
          .convertToInputWith(dummyTxId, 0)

        new EmissionsBox(emissionsBox, emissionsContract)
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
