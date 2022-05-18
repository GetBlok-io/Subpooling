package persistence

import models.DatabaseModels.Payment
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class PaymentsTable(tag: Tag) extends Table[Payment](tag, "pool_payments") {
  def poolTag      = column[String]("pool_tag")
  def address      = column[String]("address")
  def coin         = column[String]("coin")
  def amount       = column[Double]("amount")
  def txId         = column[String]("tx")
  def tokens       = column[Option[String]]("tokens")
  def created      = column[LocalDateTime]("created")
  def block        = column[Long]("block")
  def g_epoch      = column[Long]("g_epoch")
  def *            = (poolTag, address, coin, amount, txId, tokens, created, block, g_epoch) <> (Payment.tupled, Payment.unapply)
}





