package utils

import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.emissions.{EmissionsContract, ExchangeContract, HybridExchangeContract, NFTExchangeContract, ProportionalEmissionsContract}
import io.getblok.subpooling_core.contracts.holding.{AdditiveHoldingContract, TokenHoldingContract}
import io.getblok.subpooling_core.contracts.plasma.{PlasmaHoldingContract, PlasmaScripts}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import org.ergoplatform.appkit.{Address, Eip4Token, ErgoClient, ErgoId, ErgoToken, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import persistence.Tables
import slick.jdbc.PostgresProfile

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.{Failure, Try}

class EmissionGenerator(client: ErgoClient, wallet: NodeWallet, db: PostgresProfile#Backend#Database)(implicit ec: ExecutionContext) {
  import slick.jdbc.PostgresProfile.api._

  private val logger: Logger = LoggerFactory.getLogger("EmissionGenerator")

  def makeTestTokenTx(tag: String, emissionReward: Double = 67.5D) = {
    client.execute {
      ctx =>
        // TODO: Change creator in real token pools
        val fPoolCreator = db.run(Tables.PoolInfoTable.filter(_.poolTag === tag).map(_.creator).result.head)
        for(poolCreator <- fPoolCreator){
          val metadataContract = MetadataContract.generateMetadataContract(ctx)
          val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
          val emissionsContract = EmissionsContract.generate(ctx, wallet.p2pk, Address.create(poolCreator), holdingContract)


          val distributionToken = new ErgoToken(ErgoId.create("b7ba3da6a1d09c0ec73d655779bfbf933b5306002747b1c9eedcadee6b9e6994"), 10000 * Helpers.OneErg)

          val inputBoxes     = ctx.getCoveringBoxesFor(wallet.p2pk, Helpers.MinFee * 10, Seq().asJava).getBoxes
          val emissionsToken = new Eip4Token(inputBoxes.get(0).getId.toString, 1L, "GetBlok.io Token Emissions Test", "Test token identifying an emissions box", 0)

          val outBox     = ctx.newTxBuilder().outBoxBuilder()
            .contract(wallet.pk.contract)
            .value(Helpers.MinFee * 10)
            .mintToken(emissionsToken)
            .build()
          val unsignedTokenTx = ctx.newTxBuilder().boxesToSpend(inputBoxes).outputs(outBox).fee(Helpers.MinFee).sendChangeTo(wallet.p2pk.getErgoAddress).build()
          val signedTokenTx   = wallet.prover.sign(unsignedTokenTx)
          val tokenTxId       = ctx.sendTransaction(signedTokenTx).replace("\"", "")
          val tokenInputBox   = outBox.convertToInputWith(tokenTxId, 0.toShort)


          val tokenInputs = ctx.getCoveringBoxesFor(wallet.p2pk, Helpers.MinFee, Seq(distributionToken).asJava).getBoxes
            .asScala.toSeq.filter(i => i.getTokens.size() > 0).filter(i => i.getTokens.get(0).getId == distributionToken.getId)
          val emissionsOutBox = EmissionsContract.buildGenesisBox(ctx, emissionsContract,
            Helpers.ergToNanoErg(emissionReward), Address.create(poolCreator), emissionsToken.getId, distributionToken)
          val txB = ctx.newTxBuilder()
          val unsigned = txB.boxesToSpend((Seq(tokenInputBox)++tokenInputs).asJava)
            .outputs(emissionsOutBox)
            .fee(Helpers.MinFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .build()
          val signed = wallet.prover.sign(unsigned)
          val txId = ctx.sendTransaction(signed)

          val emissionsQuery = for { info <- Tables.PoolInfoTable if info.poolTag === tag } yield info.emissionsId
          val updateEmissions = emissionsQuery.update(emissionsToken.getId.toString)
          db.run(updateEmissions)

        }
    }
  }

  def makeNetaEmissionTx(tag: String, emTokenMintBox: InputBox) = {
    Try {
      client.execute {
        ctx =>
          //TODO: Use exact box id here if necessary
          logger.info("Building transactions to create NETA Emissions Box")
          val metadataContract = MetadataContract.generateMetadataContract(ctx)
          val holdingContract = TokenHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
          val template = EmissionTemplates.getNETATemplate(ctx.getNetworkType)
          logger.info("Metadata and holding contracts generated")
          val emissionsContract = ExchangeContract.generate(ctx, wallet.p2pk, template.swapAddress, holdingContract, template.lpNFT, template.distToken)
          logger.info("Emissions Contract generated")
          logger.info(s"EmEx address: ${emissionsContract.getAddress}")
          //Thread.sleep(60000)
          val tokenInputs = ctx.getBoxesById("a14940f6f7c1d459bee8e825bfbefebe35bba3655d9992f7a00ceb143ebef02c")
          val totalDistributionToken = tokenInputs.head.getTokens.get(0)
          logger.info(s"Token input boxes grabbed from chain. Num boxes: ${tokenInputs.length}")
          logger.info(s"Token inputs head: ${tokenInputs.head.toJson(true)}")
          val emissionsOutBox = ExchangeContract.buildGenesisBox(ctx, emissionsContract,
            template.initPercent, template.initFee, emTokenMintBox.getTokens.get(0).getId, totalDistributionToken)
          val txB = ctx.newTxBuilder()
          val unsigned = txB.boxesToSpend((Seq(emTokenMintBox) ++ Seq(tokenInputs.head)).asJava)
            .outputs(emissionsOutBox)
            .fee(Helpers.MinFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .build()
          logger.info("Signing tx.")
          val signed = wallet.prover.sign(unsigned)
          logger.info("Transaction signed. Now sending transaction")
          val txId = ctx.sendTransaction(signed)
          logger.info("Transaction sent")
          val emissionsQuery = for {info <- Tables.PoolInfoTable if info.poolTag === tag} yield info.emissionsId
          val updateEmissions = emissionsQuery.update(emTokenMintBox.getTokens.get(0).getId.toString)
          db.run(updateEmissions)
          logger.info("Finished making NETA Emissions Box")
      }
    }.recoverWith{
      case e: Exception =>
        logger.error("A fatal exception occurred while making neta pool", e)
        Failure(e)
    }
  }

  def makeErgoPadEmissions(tag: String, emTokenMintBox: InputBox) = {
    Try {
      client.execute {
        ctx =>
          //TODO: Use exact box id here if necessary
          logger.info("Building transactions to create ErgoPad Emissions Box")
          val template = EmissionTemplates.getErgoPadTemplate(ctx.getNetworkType)
          val emissionsContract = HybridExchangeContract.generate(ctx, wallet.p2pk, template.swapAddress,
            PlasmaHoldingContract.generate(ctx, wallet.p2pk, ErgoId.create(tag), PlasmaScripts.DUAL),
            template.lpNFT, template.distToken
          )

          logger.info("Emissions Contract generated")
          logger.info(s"HyExEm address: ${emissionsContract.getAddress}")
          //Thread.sleep(60000)
          val tokenInputs = ctx.getBoxesById("d2ebcfde059685db32fdfece7a4938f6bc7adf3fcf8b16f15b9e375973be1d67")
          val emissionToken = emTokenMintBox.getTokens.get(0)
          logger.info(s"Token input boxes grabbed from chain. Num boxes: ${tokenInputs.length}")
          logger.info(s"Token inputs head: ${tokenInputs.head.toJson(true)}")
          val emissionsOutBox = HybridExchangeContract.buildGenesisBox(
            ctx, emissionsContract, template.percent, 3000L, template.proportion, emissionToken.getId,
            new ErgoToken(template.distToken, template.totalEmissions)
          )
          val txB = ctx.newTxBuilder()
          val unsigned = txB.boxesToSpend((Seq(emTokenMintBox) ++ Seq(tokenInputs.head)).asJava)
            .outputs(emissionsOutBox)
            .fee(Helpers.MinFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .build()
          logger.info("Signing tx.")
          val signed = wallet.prover.sign(unsigned)
          logger.info("Transaction signed. Now sending transaction")
          val txId = ctx.sendTransaction(signed)
          logger.info("Transaction sent")
          val emissionsQuery = for {info <- Tables.PoolInfoTable if info.poolTag === tag} yield info.emissionsId
          val updateEmissions = emissionsQuery.update(emTokenMintBox.getTokens.get(0).getId.toString)
          db.run(updateEmissions)
          logger.info("Finished making NETA Emissions Box")
      }
    }.recoverWith{
      case e: Exception =>
        logger.error("A fatal exception occurred while making neta pool", e)
        Failure(e)
    }
  }

  def makeAnetaPlasmaEmissions(tag: String, emTokenMintBox: InputBox) = {
    Try {
      client.execute {
        ctx =>
          //TODO: Use exact box id here if necessary
          logger.info("Building transactions to create ErgoPad Emissions Box")
          val template = EmissionTemplates.getNETATemplate(ctx.getNetworkType)
          val emissionsContract = NFTExchangeContract.generate(
            ctx, wallet.p2pk, template.swapAddress,
            PlasmaHoldingContract.generate(ctx, wallet.p2pk, ErgoId.create(tag), PlasmaScripts.SINGLE_TOKEN),
            template.lpNFT, template.distToken
          )

          logger.info("Emissions Contract generated")
          logger.info(s"NFT EXCHANGE address: ${emissionsContract.getAddress}")
          //Thread.sleep(60000)
          val tokenInputs = ctx.getBoxesById("fd6bd32e111392ac5b923842ae9399164e97f0dd5b1e3979deaeb51b729245f8")
          val emissionToken = emTokenMintBox.getTokens.get(0)
          logger.info(s"Token input boxes grabbed from chain. Num boxes: ${tokenInputs.length}")
          logger.info(s"Token inputs head: ${tokenInputs.head.toJson(true)}")
          val emissionsOutBox = NFTExchangeContract.buildGenesisBox(
            ctx, emissionsContract, template.initPercent, template.initFee, emissionToken.getId,
            new ErgoToken(template.distToken, template.totalEmissions)
          )
          val txB = ctx.newTxBuilder()
          val unsigned = txB.boxesToSpend((Seq(emTokenMintBox) ++ Seq(tokenInputs.head)).asJava)
            .outputs(emissionsOutBox)
            .fee(Helpers.MinFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .build()
          logger.info("Signing tx.")
          val signed = wallet.prover.sign(unsigned)
          logger.info("Transaction signed. Now sending transaction")
          val txId = ctx.sendTransaction(signed)
          logger.info("Transaction sent")
          val emissionsQuery = for {info <- Tables.PoolInfoTable if info.poolTag === tag} yield info.emissionsId
          val updateEmissions = emissionsQuery.update(emTokenMintBox.getTokens.get(0).getId.toString)
          db.run(updateEmissions)
          logger.info("Finished making NETA Emissions Box")
      }
    }.recoverWith{
      case e: Exception =>
        logger.error("A fatal exception occurred while making neta pool", e)
        Failure(e)
    }
  }

  def makeCometEmissionTx(tag: String, emTokenMintBox: InputBox) = {
    client.execute{
      ctx =>

        logger.info("Building transactions to create COMET Emissions Box")
        val metadataContract = MetadataContract.generateMetadataContract(ctx)
        val holdingContract = AdditiveHoldingContract.generateHoldingContract(ctx, metadataContract.toAddress, ErgoId.create(tag))
        val template = EmissionTemplates.getCOMETTemplate(ctx.getNetworkType)
        logger.info("Metadata and holding contracts generated")
        val emissionsContract = ProportionalEmissionsContract.generate(ctx, wallet.p2pk, template.swapAddress, holdingContract, template.distToken,
          template.decimalPlaces)
        logger.info("Emissions Contract generated")

        val totalDistributionToken = new ErgoToken(template.distToken, template.totalEmissions)
        val tokenInputs = ctx.getBoxesById("fbdeaacba434569f0412ae7a1b82b6f05e827847ce1e8fca8735d5f0bd740470")
        logger.info("Token input boxes grabbed from chain")
        val emissionsOutBox = ProportionalEmissionsContract.buildGenesisBox(ctx, emissionsContract,
          template.initProportion, template.initFee, emTokenMintBox.getTokens.get(0).getId, totalDistributionToken)
        val txB = ctx.newTxBuilder()
        val unsigned = txB.boxesToSpend((Seq(emTokenMintBox)++tokenInputs).asJava)
          .outputs(emissionsOutBox)
          .fee(Helpers.MinFee)
          .sendChangeTo(wallet.p2pk.getErgoAddress)
          .build()
        logger.info("Signing tx.")
        val signed = wallet.prover.sign(unsigned)
        val txId = ctx.sendTransaction(signed)
        logger.info("Sending transaction")
        val emissionsQuery = for { info <- Tables.PoolInfoTable if info.poolTag === tag } yield info.emissionsId
        val updateEmissions = emissionsQuery.update(emTokenMintBox.getTokens.get(0).getId.toString)
        db.run(updateEmissions)
        logger.info("Finished making COMET Emissions Box")
    }
  }
}

object EmissionGenerator {
  def apply(client: ErgoClient, wallet: NodeWallet, db: PostgresProfile#Backend#Database)(implicit ec: ExecutionContext): EmissionGenerator = {
    new EmissionGenerator(client, wallet, db)
  }
}
