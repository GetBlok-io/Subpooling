package plasma_test

import io.getblok.subpooling_core.boxes.HybridExchangeBox
import io.getblok.subpooling_core.contracts.emissions.HybridExchangeContract
import io.getblok.subpooling_core.contracts.holding.SimpleHoldingContract
import io.getblok.subpooling_core.explorer.ExplorerHandler
import io.getblok.subpooling_core.global.AppParameters.{NodeWallet, PK}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.plasma.StateMiner
import io.getblok.subpooling_core.registers.PoolFees
import io.getblok.subpooling_core.states.models.PlasmaMiner
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoId, ErgoProver, ErgoToken, InputBox, NetworkType, OutBox, RestApiErgoClient}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import plasma_test.PlasmaHybridExchangeSuite.{buildUserBox, creatorAddress, dummyProver, dummyTokenId, dummyWallet, ergoClient, ergopadLPNFT, ergopadToken, explorerHandler, logger, sigmaTrue, toInput}

import scala.jdk.CollectionConverters.seqAsJavaListConverter

class PlasmaHybridExchangeSuite extends AnyFunSuite {

  ergoClient.execute{
    ctx =>

      val contract = HybridExchangeContract.generate(ctx, creatorAddress, sigmaTrue,
        Address.create(HybridExchangeContract.TESTNET_SWAP_ADDRESS).toErgoContract,
        ergopadLPNFT, ergopadToken)

      val box = toInput (HybridExchangeContract.buildGenesisBox(ctx, contract, 10000L, 3000L, 10000L, dummyTokenId,
        new ErgoToken(ergopadToken, 10000000000000L)))

      val hybridExBox = new HybridExchangeBox(box, contract)
      val reward = 48 * Helpers.OneErg
      val afterFee = reward - ((reward) * 3000) / PoolFees.POOL_FEE_CONST
      logger.info(s"Reward after fee: ${Helpers.nanoErgToErg(afterFee)}")
      val cycleResults = contract.cycleEmissions(ctx, hybridExBox, afterFee, None)
      val dummyBox = buildUserBox(reward)
      val feeBox = buildUserBox(Helpers.MinFee * 1)
      val tx = ctx.newTxBuilder()
        .boxesToSpend((Seq(hybridExBox.asInput, dummyBox).asJava))
        .withDataInputs(Seq(cycleResults.lpBox).asJava)
        .outputs(cycleResults.outputs:_*)
        .fee(feeBox.getValue.toLong)
        .sendChangeTo(creatorAddress.getErgoAddress)
        .build()

      val sTx = dummyProver.sign(tx)
      logger.info(s"${sTx.toJson(true)}")
  }
}

object PlasmaHybridExchangeSuite {

  val ergoClient: ErgoClient = RestApiErgoClient.create("http://188.34.207.91:9053/", NetworkType.MAINNET, "", RestApiErgoClient.defaultMainnetExplorerUrl)
  val explorerHandler = new ExplorerHandler(NetworkType.MAINNET)

  val dummyTxId = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"
  val dummyToken = "f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819"
  val dummyTokenId = ErgoId.create(dummyToken)

  val ergopadToken = ErgoId.create("d71693c49a84fbbecd4908c94813b46514b18b67a99952dc1e6e4791556de413")
  val ergopadLPNFT = ErgoId.create("d7868533f26db1b1728c1f85c2326a3c0327b57ddab14e41a2b77a5d4c20f4b2")

  def toInput(outBox: OutBox) = outBox.convertToInputWith(dummyTxId, 0)

  def dummyProver: ErgoProver = {
    ergoClient.execute{
      ctx =>
        val prover = ctx.newProverBuilder()
          .withDLogSecret(BigInt.apply(0).bigInteger)
          .build()

        return prover
    }
  }
  val creatorAddress: Address = dummyProver.getAddress
  val sigmaTrue: Address = Address.create("9fx8BdDzC5PqEBXNxTHgPYoCvpmCy8gfPTYSdC7eXCbcRUENDA7")
  val dummyWallet: NodeWallet = NodeWallet(PK(creatorAddress), dummyProver)

  def logger: Logger = LoggerFactory.getLogger("StateTransformationSuite")
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
}
