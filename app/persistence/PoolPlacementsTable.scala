package persistence

import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolPlacement, PoolState}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class PoolPlacementsTable(tag: Tag) extends Table[PoolPlacement](tag, "subpool_placements") {
  def subpool       = column[String]("subpool")
  def subpool_id    = column[Long]("subpool_id")
  def block         = column[Long]("block")
  def holdingId     = column[String]("holding_id")
  def holdingVal    = column[Long]("holding_val")
  def miner         = column[String]("miner")
  def score         = column[Long]("score")
  def minpay        = column[Long]("minpay")
  def epochsMined   = column[Long]("epochs_mined")
  def amount        = column[Long]("amount")
  def epoch         = column[Long]("epoch")
  def gEpoch        = column[Long]("g_epoch")
  def amountTwo     = column[Option[Long]]("amounttwo")

  def creator       = column[String]("creator")

  def updated       = column[LocalDateTime]("updated")
  def created       = column[LocalDateTime]("created")

  def * =   (subpool, subpool_id, block, holdingId, holdingVal,
    miner, score, minpay, epochsMined, amount, epoch, gEpoch, amountTwo) <> (PoolPlacement.tupled, PoolPlacement.unapply)
}
