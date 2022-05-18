package persistence

import io.getblok.subpooling_core.persistence.models.Models.MinerSettings
import models.DatabaseModels.MinerStats
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class MinerSettingsTable(tag: Tag) extends Table[MinerSettings](tag, "miner_settings") {
  def address          = column[String]("address")
  def paymentThreshold = column[Double]("paymentthreshold")
  def created          = column[LocalDateTime]("created")
  def updated          = column[LocalDateTime]("updated")
  def subpool          = column[String]("subpool")

  def *                = (address, paymentThreshold, created, updated, subpool) <> (MinerSettings.tupled, MinerSettings.unapply)
}


