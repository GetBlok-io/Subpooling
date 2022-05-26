package persistence

import io.getblok.subpooling_core.persistence.models.Models.PoolState
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class PoolStatesTable(tag: Tag) extends Table[PoolState](tag, "subpool_states") {
  def subpool       = column[String]("subpool")
  def subpool_id    = column[Long]("subpool_id")
  def title         = column[String]("title")
  def box           = column[String]("box")
  def tx            = column[String]("tx")
  def gEpoch        = column[Long]("g_epoch")
  def epoch         = column[Long]("epoch")
  def gHeight       = column[Long]("g_height")
  def height        = column[Long]("height")
  def status        = column[String]("status")
  def members       = column[Int]("members")
  def block         = column[Long]("block")
  def creator       = column[String]("creator")
  def storedId      = column[String]("stored_id")
  def storedVal     = column[Long]("stored_val")
  def updated       = column[LocalDateTime]("updated")
  def created       = column[LocalDateTime]("created")

  def * =   (subpool, subpool_id, title, box, tx, gEpoch, epoch,
              gHeight, height, status, members, block, creator, storedId,
              storedVal, updated, created) <> (PoolState.tupled, PoolState.unapply)
}
