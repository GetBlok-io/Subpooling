package group_test

import io.getblok.subpooling_core.boxes.MetadataInputBox
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.command.{CommandContract, PKContract}
import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, HoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.global.AppParameters.{NodeWallet, PK}
import io.getblok.subpooling_core.groups.entities.{Member, Pool}
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolPlacement
import io.getblok.subpooling_core.registers.MemberInfo
import okhttp3.OkHttpClient
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoId, ErgoProver, InputBox, NetworkType, OutBox, Parameters, RestApiErgoClient}
import plasma_test.FullStateTransformationSuite.dummyTxId

import java.util.concurrent.TimeUnit

object MockData {
  val mockAddressStrings: Array[String] = Array(
    "9fWTUhUciLHBLVswD51rnAB8QLyqC78YbYXSQMNAFbFVSwGeRUh",
    "9hY9NPMUCfLaKhyKTVYSxU8yRT6RR9houGndu2Dv8pwJZZGZyjh",
    "9hswJvM7TzPibmmqyp57VHEkF56kFujbuMmLfBvJXtsetotx6n7",
    "9fy7J6e5BgyPH4eQ6pGC96wBEQKRCKnbrZNvjemoJagXNFVZ86K",
    "9iJ3fnhmKrJi5FN5drv84UKmhxoPfep5w6aoKZGrLdA9Gxwfw3T",
    "9hY6j6DYcT9WsADwuqCrGqGHoWtvV1WQVXb3HFQR2PJz3ae4VYm",
    "9g2Ds7SMi92WRzP2NkmB6gkoYSFVyRkQqPC7mxACwkS88RtfC8g",
    "9fL7z6Fqvf3rpGPq2cm1XdMQqdEjTWdmYmZ45tVZ8jJd3Z55DGX",
    "9iN8LyoRzRD2hV5bvynG9YioPzfb32mC6Y5vA8Zg8z8cUfrgyTJ",
    "9gC75dUMwzkzZJkZkhKMfyH3vaz6nsZUC9MAzYae3xV3pBLLjHt",
  )

  val emissionsOperator: Address = Address.create("9egR7gQz5j9GL2xDfnFujNQGHXSGzDeSejv5x68SwtXiJ9Dm6Lb")
  val shareOperator: Address = Address.create("9gb9HySsXrj7817oVWaiY2N1iZT78mp5nmU1wWvNWPirJUvY3jB")

  val mockAddresses: Array[Address] = mockAddressStrings.map(Address.create)

  val builder = new OkHttpClient().newBuilder()
    .callTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
    .connectTimeout(60, TimeUnit.SECONDS)
  val ergoClient: ErgoClient = RestApiErgoClient.createWithHttpClientBuilder("http://213.239.193.208:9052/",
    NetworkType.TESTNET, "", RestApiErgoClient.defaultTestnetExplorerUrl, builder)
  val creatorAddress: Address = Address.create("4MQyML64GnzMxZgm")
  val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"
  val dummyToken = "f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819"
  val dummyDistributionToken = "472c3d4ecaa08fb7392ff041ee2e6af75f4a558810a74b28600549d5392810e8"
  val dummyEmissionsToken = "88cbad09a5b05389c97df4261fa7a1a6aeb6a4eff5bd6942697cb2925a16d0b1"
  val dummyWallet: NodeWallet = NodeWallet(PK(dummyProver.getAddress), dummyProver)
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
  val additiveHoldingContract: HoldingContract = {
    ergoClient.execute {
      ctx =>
        val txB = ctx.newTxBuilder()
        val metadataContract = MetadataContract.generateTestContract(ctx)
        val subpoolToken = ErgoId.create(dummyToken)
        AdditiveHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, subpoolToken)
    }
  }

  object SingleDistributionData {
    // Init Mock data
    val holdingValue: Long = Parameters.OneErg * 66
    val singlePool: Pool = initSinglePool
    val initSingleMembers: Array[Member] = mockAddresses.map(a => Member(a, new MemberInfo(Array(5L, Parameters.OneErg * 20, 0, 0, 0))))
    val initPartialPlacements: Array[PoolPlacement] = {

      mockAddresses.map(a => PoolPlacement(dummyToken, 0L, 0L, "", 0L,
        a.toString, randomShareScore, randomMinPay, 1L, 0L, 0L, 0L))
    }
    val initSingleHoldingMap: Map[MetadataInputBox, InputBox] = Map(singlePool.subPools.head.box -> buildHoldingBox(holdingValue))
    val initSingleAdditiveHoldingMap: Map[MetadataInputBox, InputBox] = Map(singlePool.subPools.head.box -> buildAdditiveHoldingBox(holdingValue))
    val initValueAfterFees: Long = holdingValue - (holdingValue / 100)
  }

  object HoldingData {
    // Init Mock data
    val holdingValue: Long = 63019232000L
    val totalEmissions: Long = Parameters.OneErg * 3000000
    val emissionsReward: Long = Parameters.OneErg * 66
    val singlePool: Pool = initSinglePool
    val initSingleMembers: Array[Member] = mockAddresses.map(a => Member(a, new MemberInfo(Array(randomShareScore, randomMinPay, 0, 0, 0))))
    val baseFeePerc: Map[Address, Double] = Map(Address.create("9gZsTqubics5VyrWZ2aXUy6HYUctyne6BrTzAeQFMqw4CNMxLVq") -> 1.0)
    val baseFeeMap: Map[Address, Long] = Map(Address.create("9gZsTqubics5VyrWZ2aXUy6HYUctyne6BrTzAeQFMqw4CNMxLVq") -> Parameters.MinFee * 10)
    val initPartialPlacements: Array[PoolPlacement] = {

      mockAddresses.map(a => PoolPlacement(dummyToken, 0L, 0L, "", 0L,
        a.toString, randomShareScore, randomMinPay, 1L, 0L, 0L, 0L))
    }
    val initSingleHoldingMap: Map[MetadataInputBox, InputBox] = Map(singlePool.subPools.head.box -> buildHoldingBox(holdingValue))
    val initValueAfterFees: Long = holdingValue - (holdingValue / 100)

  }
  def toInput(outBox: OutBox) = outBox.convertToInputWith(dummyTxId, 0)

  def dummyProver: ErgoProver = {
    ergoClient.execute {
      ctx =>
        val prover = ctx.newProverBuilder()
          .withDLogSecret(BigInt.apply(0).bigInteger)
          .build()

        return prover
    }
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
