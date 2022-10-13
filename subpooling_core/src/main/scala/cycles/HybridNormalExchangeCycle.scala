//package io.getblok.subpooling_core
//package cycles
//
//import contracts.emissions.HybridExchangeContract
//import contracts.plasma.{PlasmaHoldingContract, PlasmaScripts}
//import cycles.models.{Cycle, CycleResults, CycleState, EmissionResults}
//import explorer.ExplorerHandler
//import global.AppParameters.{NodeWallet, PK}
//import global.{AppParameters, Helpers}
//import persistence.models.PersistenceModels.PoolPlacement
//import registers.PoolFees
//import states.TxSendException
//
//import org.ergoplatform.appkit.impl.ErgoTreeContract
//import org.ergoplatform.appkit._
//import org.slf4j.{Logger, LoggerFactory}
//
//import java.util.NoSuchElementException
//import scala.jdk.CollectionConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
//import scala.util.{Failure, Try}
//
//class HybridNormalExchangeCycle(ctx: BlockchainContext, wallet: NodeWallet, reward: Long, fee: Long,
//                                proportion: Long, percent: Long, poolOp: Address, poolNFT: ErgoId,
//                                emNFT: ErgoId, distToken: ErgoId, exchangeRate: Long, explorerHandler: ExplorerHandler)
//                          extends Cycle {
//  private val logger: Logger = LoggerFactory.getLogger("HybridExchangeCycle")
//
//
//  def getEmissionsBox: InputBox = {
//    logger.info(s"Searching for emissions box with NFT ${emNFT}")
//    val emissionBoxes = explorerHandler
//      .boxesByTokenId(emNFT, 0, 100)
//      .getOrElse(throw new EmissionsBoxNotFoundException(emNFT))
//
//    emissionBoxes
//      .find(b => b.assets.exists(a => a.id.toString == emNFT.toString))
//      .flatMap(b => ctx.getBoxesById(b.id.toString).headOption)
//      .getOrElse(throw new EmissionsBoxNotFoundException(emNFT))
//  }
//
//
//  def simulateSwap: EmissionResults = {
//
//    logger.info(s"LP Box found with id ${lpBox.getId}")
//    val ergAfterFees = reward - ((reward * fee) / PoolFees.POOL_FEE_CONST)
//    val feeTaken = reward - ergAfterFees
//    val amountToSwap = (ergAfterFees * proportion) / PoolFees.POOL_FEE_CONST
//    val amountInErg  = ergAfterFees - amountToSwap
//
//    val assetX = lpBox.getValue.toLong
//    val assetYToken = lpBox.getTokens.get(2)
//    val assetY = assetYToken.getValue.longValue()
//
//    logger.info(s"AssetX: ${assetX}")
//    logger.info(s"AssetY: ${assetY}")
//    logger.info(s"AssetY Id: ${assetYToken.getId}")
//
//
//    val outputTokens = HybridExchangeContract.calculateMinOutputAmount(amountToSwap, 0.01, assetX ,
//      assetY, lpBox.getRegisters.get(0).asInstanceOf[ErgoValue[Int]].getValue.toLong,
//      1000)
//
//    val rate = HybridExchangeContract.calculateMinOutputAmount(Helpers.OneErg, 0.01, lpBox.getValue.toLong,
//      lpBox.getTokens.get(2).getValue.toLong, lpBox.getRegisters.get(0).asInstanceOf[ErgoValue[Int]].getValue.toLong,
//      1000).toDouble
//
//    val adjustedOutput = outputTokens + ((outputTokens * percent) / PoolFees.POOL_FEE_CONST)
//
//    val emissionRate: Double = ((adjustedOutput.toDouble) / (amountToSwap.toDouble / Helpers.OneErg))
//
//    EmissionResults(lpBox, adjustedOutput, amountToSwap, amountInErg, feeTaken, emissionRate, Some(rate))
//  }
//
//  def morphPlacementValues(placements: Seq[PoolPlacement], emissionResults: EmissionResults): Seq[PoolPlacement] = {
//    val totalScore = placements.map(_.score).sum
//    // We give all miners on the pool at least 1 token (not 1.0), to deal with issues relating to low token decimals
//    val emissionsAfterInit = emissionResults.amountEmitted - placements.size
//    require(emissionsAfterInit > 0)
//    placements.map{
//      p =>
//        p.copy(
//          amount = ((BigDecimal(p.score) / totalScore) * emissionResults.ergRewarded).longValue(),
//          amountTwo = Some(1 + ((BigDecimal(p.score) / totalScore) * emissionsAfterInit).longValue())
//        )
//    }
//  }
//
//  def morphPlacementHolding(placements: Seq[PoolPlacement], holdingBox: InputBox): Seq[PoolPlacement] = {
//    placements.map{
//      p =>
//        p.copy(
//          holding_id = holdingBox.getId.toString,
//          holding_val = holdingBox.getValue.longValue()
//        )
//    }
//  }
//
//  def cycle(cycleState: CycleState, emissionResults: EmissionResults, sendTxs: Boolean = true): CycleResults = {
//    val reArranged = CycleHelper.reArrange(ctx, wallet, cycleState.inputBoxes, reward, AppParameters.groupFee * 10)
//    val rewardInput = reArranged._1
//
//    val inputs = Seq(cycleState.cycleBox) ++ rewardInput
//    val amountLeft = cycleState.cycleBox.getTokens.get(1).getValue.toLong - emissionResults.amountEmitted
//    val holdingContract = PlasmaHoldingContract.generate(ctx, wallet.p2pk, poolNFT, PlasmaScripts.DUAL)
//
//
//    val nextEmissions = ctx.newTxBuilder().outBoxBuilder()
//      .contract(new ErgoTreeContract(cycleState.cycleBox.getErgoTree, ctx.getNetworkType))
//      .value(cycleState.cycleBox.getValue.toLong)
//      .registers(cycleState.cycleBox.getRegisters.asScala.toSeq:_*)
//      .tokens(
//        cycleState.cycleBox.getTokens.get(0),
//        new ErgoToken(distToken, amountLeft)
//      )
//      .build()
//
//    val nextHoldingBox = ctx.newTxBuilder().outBoxBuilder()
//      .contract(holdingContract)
//      .value(emissionResults.ergRewarded)
//      .tokens(new ErgoToken(distToken, emissionResults.amountEmitted))
//      .build()
//
//    val nextExchange = ctx.newTxBuilder().outBoxBuilder()
//      .contract(PK(poolOp).contract)
//      .value(emissionResults.ergTaken)
//      .build()
//
//    val nextPoolFee = ctx.newTxBuilder().outBoxBuilder()
//      .contract(AppParameters.getFeeAddress.toErgoContract)
//      .value(emissionResults.feeTaken)
//      .build()
//
//    val uTx = ctx.newTxBuilder()
//      .boxesToSpend(inputs.asJava)
//      .outputs(nextEmissions, nextHoldingBox, nextExchange, nextPoolFee)
//      .withDataInputs(Seq(emissionResults.lpBox).asJava)
//      .fee(AppParameters.groupFee * 10)
//      .sendChangeTo(AppParameters.getFeeAddress.getErgoAddress)
//      .build()
//
//    val signed = wallet.prover.sign(uTx)
//    val nextCycleBox = signed.getOutputsToSpend.get(0)
//    val holdingBox = signed.getOutputsToSpend.get(1)
//
//    if(sendTxs){
//      val sentTxs = {
//        Try{
//          logger.info("Now sending reArrange tx")
//          val reId = ctx.sendTransaction(reArranged._2)
//          logger.info(s"ReArrange tx sent with id ${reId}!")
//          Thread.sleep(2000)
//          logger.info("Now sending emission tx")
//          val emId = ctx.sendTransaction(signed)
//          logger.info(s"Emission tx sent with id ${emId}!")
//        }.recoverWith{
//          case t: Throwable =>
//            logger.error("There was a fatal error while sending transactions!", t)
//            Failure(t)
//        }
//      }
//
//      if(sentTxs.isFailure)
//        throw new TxSendException("Failed to send transactions for hybrid emissions!")
//    }
//
//    CycleResults(nextCycleBox, holdingBox, signed, reArranged._2, emissionResults, amountLeft)
//  }
//
//
//
//
//}
