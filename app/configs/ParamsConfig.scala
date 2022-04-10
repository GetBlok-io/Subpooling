package configs

import io.getblok.subpooling_core.global.AppParameters
import io.getblok.subpooling_core.persistence.models.Models.DbConn
import play.api.Configuration

import java.sql.DriverManager
import java.util.Properties

class ParamsConfig(config: Configuration){

  private val window = config.get[Double]("params.pplnsWindow")
  private val adjustCoeff = config.get[Long]("params.scoreAdjustCoeff")
  AppParameters.pplnsWindow = BigDecimal(window)
  AppParameters.scoreAdjustmentCoeff = adjustCoeff
}
