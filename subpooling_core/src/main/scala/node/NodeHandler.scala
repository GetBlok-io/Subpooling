package io.getblok.subpooling_core
package node

import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.node.NodeHandler.{ConfirmedBlock, OrphanBlock, PartialBlockInfo, ValidBlock}
import io.getblok.subpooling_core.persistence.models.Models.Block
import org.ergoplatform.appkit.{Address, ErgoClient, NetworkType, RestApiErgoClient}
import org.ergoplatform.explorer.client.model.{Items, ItemsA}
import org.ergoplatform.explorer.client.{DefaultApi, ExplorerApiClient}
import org.ergoplatform.restapi.client.{ApiClient, BlocksApi, InlineResponse2006, MiningApi}
import org.slf4j.{Logger, LoggerFactory}
import retrofit2.Response

import java.time.{Instant, LocalDateTime}
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.util.Try

class NodeHandler(apiClient: ApiClient, ergoClient: ErgoClient) {
  val blockService: BlocksApi = apiClient.createService(classOf[BlocksApi])
  val minerService: CustomMiningAPI = apiClient.createService(classOf[CustomMiningAPI])

  val logger: Logger = LoggerFactory.getLogger("NodeHandler")


  def getMinerPK: String = {
    val minerPKResponse = minerService.miningReadMinerRewardPubkey().execute()
    val minerPKOpt = asOption[MinerPKResponse](minerPKResponse).map(i => i.getRewardPubKey)
    if(minerPKOpt.isDefined)
      minerPKOpt.get
    else {
      logger.info("Couldn't get any miner PK, defaulting to value in config")
      AppParameters.defaultMiningPK
    }

  }

  def validateBlock(blockHeight: Long): Option[NodeHandler.PartialBlockInfo] = {
    val blockHashResponse = blockService.getFullBlockAt(blockHeight.toInt).execute()
    val blockHash = asOption(blockHashResponse).map(l => l.asScala.toSeq)
    if (blockHash.isDefined) {
      if (blockHash.get.nonEmpty) {
        logger.info(s"Current block hashes at height $blockHeight: ${blockHash.get.mkString}")
        val fullBlockResponse = blockService.getFullBlockById(blockHash.get.head).execute()
        val fullBlock = asOption(fullBlockResponse)
        if (fullBlock.isDefined) {
          val currentTime = Instant.now().toEpochMilli
          val blockTime = fullBlock.get.getHeader.getTimestamp
          // If time is less than 10 minutes since block was created, do not evaluate just yet
          if (currentTime - blockTime < 600000) {
            return None
          }
          val attemptBlockValidation = Try {
            val blockNonce = fullBlock.get.getHeader.getPowSolutions.getN
            val minerPK = fullBlock.get.getHeader.getPowSolutions.getPk
            val coinbaseTx = fullBlock.get.getBlockTransactions.getTransactions.get(0)
            val rewardAddress = fullBlock.get.getBlockTransactions.getTransactions.get(0).getOutputs.get(1).getErgoTree
            val txReward = fullBlock.get.getBlockTransactions.getTransactions.get(0).getOutputs.get(1).getValue
            val txFee = fullBlock.get.getBlockTransactions.getTransactions.asScala.filter(_.getOutputs.size() == 1)
              .filter(_.getOutputs.get(0).getErgoTree == rewardAddress)
              .head.getOutputs.get(0).getValue
            var nodePK = getMinerPK
            if (nodePK == null) {
              logger.info("NodePK was null, setting to app params value: " + AppParameters.defaultMiningPK)
              nodePK = AppParameters.defaultMiningPK
            }


            if (minerPK != nodePK) {
              logger.warn(s"Block at $blockHeight had miner pk $minerPK rather than the correct $nodePK")
              Some(OrphanBlock(0.0, "none", "none"))
            } else {
              if (txReward == 0) {
                logger.info("No reward existed for transaction, classifying as empty block!")
                Some(OrphanBlock(0.0, "none", "none"))
              } else {
                Some(ValidBlock(Helpers.nanoErgToErg(txReward + txFee), blockNonce, blockHash.get.head))
              }
            }
          }
          if (attemptBlockValidation.isSuccess)
            attemptBlockValidation.get
          else
            None
        } else {
          None
        }
      } else {
        None
      }
    }else{
      logger.warn(s"There was no block hash defined for block $blockHeight, maybe a node is desynced?")
      None
    }
  }


  private def asOption[T](resp: Response[T]): Option[T] = {
    if (resp.isSuccessful)
      Some(resp.body())
    else
      None
  }

  private def itemSeq[T](opt: Option[Items[T]]) = {
    if (opt.isDefined)
      Some(opt.get.getItems.asScala.toSeq)
    else
      None
  }

  private def outputSeq(opt: Option[ItemsA]) = {
    if (opt.isDefined)
      Some(opt.get.getItems.asScala.toSeq)
    else
      None
  }
}

object NodeHandler {
  sealed trait BlockValidationResponse

  class PartialBlockInfo(val reward: Double, val txConfirmationInfo: String, val blockHash: String, val gEpoch: Long = -1L) extends BlockValidationResponse
  @Deprecated
  case class ConfirmedBlock(override val reward: Double, txConf: String, hash: String, override val gEpoch: Long)
    extends PartialBlockInfo(reward, txConf, hash, gEpoch)
  case class ValidBlock(override val reward: Double, txConf: String, hash: String) extends PartialBlockInfo(reward, txConf, hash)
  case class OrphanBlock(override val reward: Double, txConf: String, hash: String) extends PartialBlockInfo(reward, txConf, hash)

}
