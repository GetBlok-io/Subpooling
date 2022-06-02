package configs

import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.Models.DbConn
import play.api.Configuration

import java.sql.DriverManager
import java.util.Properties
import scala.concurrent.duration.FiniteDuration

class ParamsConfig(config: Configuration){

  private val window = config.get[Double]("params.pplnsWindow")
  private val adjustCoeff = config.get[Long]("params.scoreAdjustCoeff")

  val pendingBlockNum: Int = config.get[Int]("params.pendingBlockNum")
  val confirmationNum: Int = config.get[Int]("params.confirmationNum")
  val numToValidate:   Int = config.get[Int]("params.numToValidate")
  val defaultPK: String = config.get[String]("params.defaultMinerPK")
  val autoConfirmGroups: Boolean = config.get[Boolean]("params.autoConfirmGroups")
  val parallelPoolPlacements: Boolean = config.get[Boolean]("params.parallelPoolPlacements")
  val amountToPreCollect: Long = Helpers.ergToNanoErg(config.get[Double]("params.amountToPreCollect"))
  val restartPlacements: FiniteDuration = config.get[FiniteDuration]("params.restartPlacements")
  val restartDists: FiniteDuration = config.get[FiniteDuration]("params.restartDists")

  val blockBotToken: String = config.get[String]("params.blockBotToken")
  val blockBotChat: String = config.get[String]("params.blockBotChat")
  val enableBlockBot: Boolean = config.get[Boolean]("params.enableBlockBot")

  val defaultPoolTag: String = config.get[String]("params.defaultPool")
  val keepSharesWindowInWeeks: Int = config.get[Int]("params.keepSharesWindow")
  val keepMinerStatsWindowInWeeks: Int = config.get[Int]("params.keepMinerStatsWindow")
  val currentFeeAddress: String = config.get[String]("params.feeAddress")
  val currentFeePerc: Double    = config.get[Double]("params.feePercent")
  val regenFromChain: Boolean   = config.get[Boolean]("params.regenFromChain")
  val groupStart: Int           = config.get[Int]("params.groupStart")

  AppParameters.pplnsWindow = BigDecimal(window)
  AppParameters.scoreAdjustmentCoeff = adjustCoeff
  AppParameters.defaultMiningPK = defaultPK
  AppParameters.numMinConfirmations = confirmationNum
  AppParameters.feeAddress = currentFeeAddress
  AppParameters.feePerc = currentFeePerc
}
