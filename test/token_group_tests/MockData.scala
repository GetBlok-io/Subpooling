package io.getblok.subpooling_core
package token_group_tests

import boxes.MetadataInputBox
import contracts.MetadataContract
import contracts.command.{CommandContract, PKContract}
import contracts.holding.{HoldingContract, SimpleHoldingContract, TokenHoldingContract}
import global.AppParameters.{NodeWallet, PK}
import groups.entities.{Member, Pool}
import persistence.models.Models.PoolPlacement
import registers.MemberInfo

import org.ergoplatform.appkit._


object MockData {
  val mockAddressStrings: Array[String] = Array(
    "9g4Kek6iWspXPAURU3zxT4RGoKvFdvqgxgkANisNFbvDwK1KoxW",
    "9gqhrGQN3eQmTFAW9J6KNX8ffUhe8BmTesE45b9nBmL7VJohhtY",
    "9em1ShUCkTa43fALGgzwKQ5znuXY2dMnnfHqq4bX3wSWytH11t7",
  )

  val emissionsOperator: Address = Address.create("9egR7gQz5j9GL2xDfnFujNQGHXSGzDeSejv5x68SwtXiJ9Dm6Lb")
  val shareOperator: Address     = Address.create("9gb9HySsXrj7817oVWaiY2N1iZT78mp5nmU1wWvNWPirJUvY3jB")

  val mockAddresses: Array[Address] = mockAddressStrings.map(Address.create)


  val ergoClient: ErgoClient = RestApiErgoClient.create("http://188.34.207.91:9053/", NetworkType.MAINNET, "", "")
  val creatorAddress: Address = Address.create("4MQyML64GnzMxZgm")
  val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"
  val dummyToken = "f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819"
  val dummyDistributionToken = "472c3d4ecaa08fb7392ff041ee2e6af75f4a558810a74b28600549d5392810e8"
  val dummyEmissionsToken = "88cbad09a5b05389c97df4261fa7a1a6aeb6a4eff5bd6942697cb2925a16d0b1"
  val dummyWallet: NodeWallet = NodeWallet(PK(creatorAddress), dummyProver)
  val dummyTokenId: ErgoId = ErgoId.create(dummyToken)

  val commandContract: CommandContract = new PKContract(dummyWallet.p2pk)
  val holdingContract: HoldingContract = {
    ergoClient.execute {
      ctx =>
        val txB = ctx.newTxBuilder()
        val metadataContract = MetadataContract.generateTestContract(ctx)
        val subpoolToken = ErgoId.create(dummyToken)
        TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, subpoolToken)
    }
  }

  object SingleDistributionData {
    // Init Mock data
    val holdingValue: Long = Parameters.OneErg * 66
    val singlePool: Pool = initSinglePool
    val initSingleMembers: Array[Member] = mockAddresses.map(a => Member(a, new MemberInfo(Array(randomShareScore, randomMinPay, 0, 0, 0))))
    val initPartialPlacements: Array[PoolPlacement] = {

      mockAddresses.map(a => PoolPlacement(dummyToken, 0L, 0L, "", 0L,
        a.toString, randomShareScore, randomMinPay, 1L, 0L, 0L, 0L))
    }
    val initSingleHoldingMap: Map[MetadataInputBox, InputBox] = Map(singlePool.subPools.head.box -> buildHoldingBox(holdingValue))
    val initValueAfterFees: Long = holdingValue - (holdingValue / 100)
  }

  object HoldingData {
    // Init Mock data
    val holdingValue: Long = Parameters.OneErg * 66
    val totalEmissions: Long = Parameters.OneErg * 3000000
    val emissionsReward: Long = Parameters.OneErg * 66
    val singlePool: Pool = initSinglePool
    val initSingleMembers: Array[Member] = mockAddresses.map(a => Member(a, new MemberInfo(Array(randomShareScore, randomMinPay, 0, 0, 0))))
    val baseFeeMap: Map[Address, Long]   = Map(Address.create("9gZsTqubics5VyrWZ2aXUy6HYUctyne6BrTzAeQFMqw4CNMxLVq") -> randomMinPay)
    val initPartialPlacements: Array[PoolPlacement] = {

      mockAddresses.map(a => PoolPlacement(dummyToken, 0L, 0L, "", 0L,
        a.toString, randomShareScore, randomMinPay, 1L, 0L, 0L, 0L))
    }
    val initSingleHoldingMap: Map[MetadataInputBox, InputBox] = Map(singlePool.subPools.head.box -> buildHoldingBox(holdingValue))
    val initValueAfterFees: Long = holdingValue - (holdingValue / 100)

  }


  object LoadedPoolData {
    // Init Mock data
    val holdingValue: Long = Parameters.OneErg * 66
    val singlePool: Pool = initSinglePool
    val initPartialPlacements: Array[PoolPlacement] = {

      mockAddresses.map(a => PoolPlacement(dummyToken, 0L, 0L, "", 0L,
        a.toString, randomShareScore, randomMinPay, 1L, 0L, 0L, 0L))
    }
    val initSingleHoldingMap: Map[MetadataInputBox, InputBox] = Map(singlePool.subPools.head.box -> buildHoldingBox(holdingValue))
    val initValueAfterFees: Long = holdingValue - (holdingValue / 100) - (initPartialPlacements.length * Parameters.MinFee)
  }



}
