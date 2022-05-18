package persistence

import models.DatabaseModels.MinerStats
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class MinerStatsArchiveTable(tag: Tag) extends Table[MinerStats](tag, "minerstats_archive") {
  def id              = column[Long]("id")
  def poolId          = column[String]("poolid")
  def miner           = column[String]("miner")
  def worker          = column[String]("worker")
  def hashrate        = column[Double]("hashrate")
  def sharesPerSecond = column[Double]("sharespersecond")
  def created         = column[LocalDateTime]("created")

  def *            = (id, poolId, miner, worker,
                      hashrate, sharesPerSecond, created) <> (MinerStats.tupled, MinerStats.unapply)
}


