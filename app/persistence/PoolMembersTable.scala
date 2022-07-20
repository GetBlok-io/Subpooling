package persistence

import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolMember
import models.DatabaseModels.{BalanceChange}
import slick.collection.heterogeneous
import slick.collection.heterogeneous.{HCons, HList, HNil}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ProvenShape, ShapedValue, Tag}

import java.time.LocalDateTime

class PoolMembersTable(tag: Tag) extends Table[PoolMember](tag, "subpool_members") {
  def subpool       = column[String]("subpool")
  def subpool_id    = column[Long]("subpool_id")
  def tx            = column[String]("tx")
  def box           = column[String]("box")
  def g_epoch       = column[Long]("g_epoch")
  def epoch         = column[Long]("epoch")
  def height        = column[Long]("height")
  def miner         = column[String]("miner")
  def share_score   = column[Long]("share_score")
  def share         = column[Long]("share")
  def share_perc    = column[Double]("share_perc")
  def minpay        = column[Long]("minpay")
  def stored        = column[Long]("stored")
  def paid          = column[Long]("paid")
  def change        = column[Long]("change")
  def epochs_mined  = column[Long]("epochs_mined")
  def token         = column[String]("token")
  def token_paid    = column[Long]("token_paid")
  def block         = column[Long]("block")
  def created       = column[LocalDateTime]("created")

  def * =   (subpool,
    subpool_id, tx, box, g_epoch, epoch, height,
    miner, share_score, share, share_perc, minpay, stored,
    paid, change, epochs_mined, token, token_paid, block,
    created) <> (PoolMember.tupled, PoolMember.unapply)
}
