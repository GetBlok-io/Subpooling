package persistence

import io.getblok.subpooling_core.persistence.models.Models.MinerSettings
import models.DatabaseModels.{MinerStats, SMinerSettings}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class MinerSettingsTable(tag: Tag) extends Table[SMinerSettings](tag, "miner_settings") {
  def address          = column[String]("address")
  def paymentThreshold = column[Double]("paymentthreshold")
  def created          = column[LocalDateTime]("created")
  def updated          = column[LocalDateTime]("updated")
  def subpool          = column[Option[String]]("subpool")

  def *                = (address, paymentThreshold, created, updated, subpool) <> (SMinerSettings.tupled, SMinerSettings.unapply)
}


