package plasma_utils

import akka.actor.ActorRef
import configs.TasksConfig.TaskConfiguration
import configs.{Contexts, ParamsConfig}
import io.getblok.subpooling_core.contracts.voting.{ProxyBallotContract, RecordingContract}
import io.getblok.subpooling_core.global.AppParameters.NodeWallet
import io.getblok.subpooling_core.global.Helpers
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.PostgresProfile
import utils.ConcurrentBoxLoader

import scala.jdk.CollectionConverters.{iterableAsScalaIterableConverter, seqAsJavaListConverter}
import scala.language.postfixOps

class VoteCollector(client: ErgoClient, wallet: NodeWallet) {
  val logger: Logger = LoggerFactory.getLogger("VoteCollector")

  final val voteTokenId: ErgoId = ErgoId.create("60a3b2e917fe6772d65c5d253eb6e4936f1a2174d62b3569ad193a2bf6989298")
  final val recordingTokenId: ErgoId = ErgoId.create("2ab5e5c8cfd63571ad2c49db2b0aa1ea54e0f2536ad0e78a19cbb1eb38bb9813")
  final val voteEndHeight: Int = 844625
  def voteYesContract: ErgoContract = client.execute(ctx => ProxyBallotContract.generateContract(ctx, voteTokenId, true, recordingTokenId))
  def voteNoContract: ErgoContract = client.execute(ctx => ProxyBallotContract.generateContract(ctx, voteTokenId, false, recordingTokenId))
  def recordingContract: ErgoContract = client.execute(ctx => RecordingContract.generateContract(ctx,
    voteTokenId, voteYesContract.toAddress, voteNoContract.toAddress, voteEndHeight))

  def filterProxies(boxes: java.util.List[InputBox]): Seq[InputBox] = {
    boxes.asScala.toSeq.filter(_.getTokens.size() == 1).filter(_.getTokens.get(0).getId.toString == voteTokenId.toString)
  }

  def getProxies: (Seq[InputBox], Seq[InputBox]) = {
    logger.info("Getting proxy boxes with addresses:")

    logger.info(s"Yes: ${voteYesContract.toAddress.toString}")
    logger.info(s"No: ${voteNoContract.toAddress.toString}")

    client.execute{
      ctx =>
        val yesBoxes = ctx.getUnspentBoxesFor(voteYesContract.toAddress, 0, 50)
        val noBoxes  = ctx.getUnspentBoxesFor(voteNoContract.toAddress, 0, 50)

        (filterProxies(yesBoxes), filterProxies(noBoxes))
    }
  }

  def proxyTokenSum(proxies: (Seq[InputBox], Seq[InputBox])): Long = {
    proxies._1.map(_.getTokens.get(0).getValue.toLong).sum + proxies._2.map(_.getTokens.get(0).getValue.toLong).sum
  }

  def getRecordingBox: InputBox = {
    logger.info(s"Getting recording box with id ${recordingTokenId} and address: ${recordingContract.toAddress}")
    client.execute{
      ctx =>
        ctx.getUnspentBoxesFor(recordingContract.toAddress, 0, 100).asScala.toSeq
          .filter(_.getTokens.size() > 0)
          .filter(_.getTokens.get(0).getId.toString == recordingTokenId.toString)
          .head
    }
  }

  def collect(): Unit = {
    client.execute{
      ctx =>
        val recordingBox = getRecordingBox
        val proxies = getProxies

        if(proxies._1.isEmpty && proxies._2.isEmpty){
          logger.info("No proxy boxes found, now exiting collection")
        }else {
          val inputs = Seq(recordingBox) ++ proxies._1 ++ proxies._2

          val outRecording = RecordingContract.buildNextRecordingBox(ctx, recordingBox, recordingContract, proxies._1.toArray, proxies._2.toArray,
            voteTokenId)

          val uTx = ctx.newTxBuilder()
            .boxesToSpend(inputs.asJava)
            .outputs(outRecording)
            .fee(Helpers.MinFee)
            .sendChangeTo(wallet.p2pk.getErgoAddress)
            .tokensToBurn(new ErgoToken(voteTokenId, proxyTokenSum(proxies)))
            .build()

          val sTx = wallet.prover.sign(uTx)

          logger.info(s"Now sending collection transaction with id ${sTx.getId}")
          ctx.sendTransaction(sTx)
          logger.info("Transaction was sent!")

        }
    }

  }
}
