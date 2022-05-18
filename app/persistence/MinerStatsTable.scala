package persistence

import models.DatabaseModels.{Balance, MinerStats}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class MinerStatsTable(tag: Tag) extends Table[MinerStats](tag, "minerstats") {
  def id              = column[Long]("id", O.PrimaryKey)
  def poolId          = column[String]("poolid")
  def miner           = column[String]("miner")
  def worker          = column[String]("worker")
  def hashrate        = column[Double]("hashrate")
  def sharesPerSecond = column[Double]("sharespersecond")
  def created         = column[LocalDateTime]("created")

  def *            = (id, poolId, miner, worker,
                      hashrate, sharesPerSecond, created) <> (MinerStats.tupled, MinerStats.unapply)
}


