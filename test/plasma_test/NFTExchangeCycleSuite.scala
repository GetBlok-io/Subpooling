package plasma_test

import io.getblok.subpooling_core.contracts.emissions.{HybridExchangeContract, NFTExchangeContract}
import io.getblok.subpooling_core.contracts.plasma.{PlasmaHoldingContract, PlasmaScripts}
import io.getblok.subpooling_core.cycles.{HybridExchangeCycle, NFTExchangeCycle}
import io.getblok.subpooling_core.cycles.models.{CycleState, NFTHolder}
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolPlacement
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken}
import org.scalatest.funsuite.AnyFunSuite
import plasma_test.NFTExchangeCycleSuite.{netaLPNFT, netaToken, nftHolders, placements}
import plasma_test.PlasmaHybridExchangeSuite._

class NFTExchangeCycleSuite extends AnyFunSuite {

  val cycleResults = ergoClient.execute{
    ctx =>

      for(holder <- nftHolders) logger.info(s"Holder ${holder.address} with count ${holder.count}")

      val contract = NFTExchangeContract.generate(ctx, creatorAddress, sigmaTrue,
        PlasmaHoldingContract.generate(ctx, dummyWallet.p2pk, dummyTokenId, PlasmaScripts.SINGLE_TOKEN),
        netaLPNFT, netaToken)

      val box = toInput (NFTExchangeContract.buildGenesisBox(ctx, contract, 10000L, 3000L, dummyTokenId,
        new ErgoToken(netaToken, 10000000000000L)))


      val cycle = new NFTExchangeCycle(ctx, dummyWallet, 48 * Helpers.OneErg, 3000L, nftHolders,
        10000L, sigmaTrue, dummyTokenId, dummyTokenId, netaToken, netaLPNFT, explorerHandler)

      val result = cycle.simulateSwap

      logger.info(result.toString)
      val cycleState = CycleState(box, Seq(buildUserBox(Helpers.OneErg * 50)), placements)
      val results = cycle.cycle(cycleState, result, false)

      val nextPlacementValues = cycle.morphPlacementValues(placements, results.emissionResults)
      val scoreSum = placements.map(_.score).sum
      for(p <- nextPlacementValues){
        val optHolder = nftHolders.find(_.address.toString == p.miner)
        if(optHolder.isDefined){
          logger.info(s"Found nftHolder ${p.miner} with count ${optHolder.get.count}")
          val placeAmount = getBonusScore(p.score, scoreSum, results.emissionResults.amountEmitted - nextPlacementValues.length, optHolder.get.count)
          assert(p.amount == placeAmount.toLong)
          logger.info(s"Miner ${p.miner} has amount ${p.amount} with score ${p.score}! [Would be ${getNormScore(p.score, scoreSum, results.emissionResults.amountEmitted - nextPlacementValues.length).toLong} without bonus")
        }else {
          logger.info(s"Miner ${p.miner} does not hold an NFT!")
          val placeAmount = getNormScore(p.score, scoreSum, results.emissionResults.amountEmitted - nextPlacementValues.length)
          assert(p.amount == placeAmount.toLong)
          logger.info(s"Miner ${p.miner} has amount ${p.amount} with score ${p.score}")
        }
      }

  }

  def getBonusScore(score: Long, sumScore: Long, amount: Long, count: Int) = {
    val initAmnt = ((BigDecimal(score) / sumScore) * amount)

    val bonusAmnt = (initAmnt * (1 + (0.03 * count.toDouble)))
    bonusAmnt + 1
  }

  def getNormScore(score: Long, sumScore: Long, amount: Long) = {
    ((BigDecimal(score) / sumScore) * amount) + 1
  }
}

object NFTExchangeCycleSuite {
  val placements: Seq[PoolPlacement] = MockAddresses.addresses.slice(0, 100).map{
    a =>
      val random = Math.random() * 10000L
      PoolPlacement("test", 0L, 0L, "", 0L, a, random.toLong, 0L, 0L, 0L, 0L, 0L)
  }

  def randomNFTHolder: NFTHolder = {
    val random = (Math.random() * 100).toInt
    val randomCount = (Math.random() * 5).toInt
    NFTHolder(
      Address.create(placements(random).miner),
      randomCount
    )
  }
  val netaToken = ErgoId.create("472c3d4ecaa08fb7392ff041ee2e6af75f4a558810a74b28600549d5392810e8")
  val netaLPNFT = ErgoId.create("7d2e28431063cbb1e9e14468facc47b984d962532c19b0b14f74d0ce9ed459be")
  val nftHolders: Seq[NFTHolder] = for(i <- 0 to 10) yield randomNFTHolder
}


