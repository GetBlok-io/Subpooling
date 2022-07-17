package io.getblok.subpooling_core
package global

import boxes.BoxHelpers

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.{ErgoTreeContract, InputBoxImpl, NodeAndExplorerDataSourceImpl}
import sigmastate.Values.ErgoTree

import java.util
import scala.jdk.CollectionConverters.{asScalaBufferConverter, seqAsJavaListConverter}

object AppParameters {

  case class NodeWallet(pk: PK, prover: ErgoProver) {
    val p2pk: Address = pk.p2pk
    val contract: ErgoContract = pk.contract

    def boxes(ctx: BlockchainContext, amount: Long): Option[util.List[InputBox]] = {
      val dataSource = ctx.getDataSource.asInstanceOf[NodeAndExplorerDataSourceImpl]
      val response = dataSource.getNodeWalletApi.walletBoxes(0, 0).execute()
      val boxes = response.body().asScala.toSeq
      val asInputs: Seq[InputBox] = boxes.map(b => new InputBoxImpl(b.getBox))

      val boxList = BoxSelectorsJavaHelpers.selectBoxes(asInputs.asJava, amount, Seq().asJava)

      if(boxList.isEmpty)
        None
      else
        Some(boxList)
    }
  }

  case class PK(p2pk: Address) {
    val contract: ErgoContract = new ErgoTreeContract(p2pk.getErgoAddress.script, AppParameters.networkType)
    val tree: ErgoTree = p2pk.getErgoAddress.script
    val bytes: Array[Byte] = p2pk.getErgoAddress.script.bytes
  }

  var networkType: NetworkType = NetworkType.MAINNET
  val groupFee: Long = Parameters.MinFee * 6
  val commandValue: Long = Parameters.MinFee * 7
  var pplnsWindow: BigDecimal = BigDecimal("0.5")
  val shareConst: BigDecimal = BigDecimal("256")
  var scriptBasePath: String = ""
  var mcPoolId: String = "ergo1"
  var defaultMinPay: Long = Parameters.MinFee * 10
  var scoreAdjustmentCoeff: Long = 10000000L
  var defaultMiningPK = "" // default miner pk to use in case query request does not work
  var numMinConfirmations = 20
  var feeAddress = "9fMLVMsG8U1PHqHZ8JDQ4Yn6q5wPdruVn2ctwqaqCXVLfWxfc3Q"
  var feePerc    = 1.0
  var baseFeePerc = Map(Address.create(feeAddress) -> feePerc)
  var sendTxs = true
  def getFeeAddress: Address = Address.create(feeAddress)
  def getBaseFees(blockReward: Long): Map[Address, Long] = baseFeePerc.map(f => f._1 -> BoxHelpers.removeDust((BigDecimal(blockReward) * (f._2 / 100)).longValue()))
}
