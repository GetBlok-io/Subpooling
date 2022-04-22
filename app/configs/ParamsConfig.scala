package configs

import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.Models.DbConn
import play.api.Configuration

import java.sql.DriverManager
import java.util.Properties

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
  AppParameters.pplnsWindow = BigDecimal(window)
  AppParameters.scoreAdjustmentCoeff = adjustCoeff
  AppParameters.defaultMiningPK = defaultPK
  AppParameters.numMinConfirmations = confirmationNum
}
