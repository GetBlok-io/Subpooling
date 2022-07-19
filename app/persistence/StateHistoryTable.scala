package persistence

import io.getblok.subpooling_core.persistence.models.Models.PoolState
import models.DatabaseModels.StateHistory
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class StateHistoryTable(tag: Tag) extends Table[StateHistory](tag, "state_history") {
  def poolTag       = column[String]("pool_tag")
  def gEpoch        = column[Long]("g_epoch")
  def box           = column[String]("box")
  def tx            = column[String]("tx")
  def commandBox    = column[String]("command_box")
  def command       = column[String]("command")
  def status        = column[String]("status")
  def step          = column[Int]("step")
  def digest        = column[String]("digest")
  def manifest      = column[String]("manifest")
  def subTree_1     = column[String]("subtree_1")
  def subTree_2     = column[String]("subtree_2")
  def subTree_3     = column[String]("subtree_3")
  def subTree_4     = column[String]("subtree_4")
  def block         = column[Long]("block")
  def created       = column[LocalDateTime]("created")
  def updated       = column[LocalDateTime]("updated")

  def * =   (poolTag, gEpoch, box, tx, commandBox, command, status, step, digest,
              manifest, subTree_1, subTree_2, subTree_3, subTree_4, block, created,
              updated) <> (StateHistory.tupled, StateHistory.unapply)
}
