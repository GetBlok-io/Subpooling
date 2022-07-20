package persistence


import io.getblok.subpooling_core.plasma.PoolBalanceState
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class PoolBalanceStateTable(tag: Tag) extends Table[PoolBalanceState](tag, "pool_balance_states") {
  def poolTag       = column[String]("pool_tag")
  def gEpoch        = column[Long]("g_epoch")
  def tx            = column[String]("tx")
  def digest        = column[String]("digest")
  def step          = column[Int]("step")
  def command       = column[String]("command")
  def miner         = column[String]("miner")
  def minerHash     = column[String]("miner_hash")
  def balance       = column[Long]("balance")
  def lastPaid      = column[Long]("last_paid")
  def block         = column[Long]("block")
  def created       = column[LocalDateTime]("created")
  def updated       = column[LocalDateTime]("updated")

  def * =   (poolTag, gEpoch, tx, digest, step, command, miner,
              minerHash, balance, lastPaid, block, created, updated) <> (PoolBalanceState.tupled, PoolBalanceState.unapply)
}
