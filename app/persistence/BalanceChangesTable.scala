package persistence

import models.DatabaseModels.BalanceChange
import slick.ast.TypedType
import slick.jdbc.PostgresProfile.api._
import slick.lifted.MappedToBase.mappedToIsomorphism
import slick.lifted.Tag

import java.time.LocalDateTime

class BalanceChangesTable(tag: Tag) extends Table[BalanceChange](tag, "pool_balance_changes") {
  def poolTag      = column[String]("pool_tag")
  def address      = column[String]("address")
  def coin         = column[String]("coin")
  def amount       = column[Double]("amount")
  def txId         = column[String]("tx")
  def tokens       = column[Option[String]]("tokens")
  def created      = column[LocalDateTime]("created")
  def block        = column[Long]("block")
  def g_epoch      = column[Long]("g_epoch")
  def *            = (poolTag, address, coin, amount, txId, tokens, created, block, g_epoch) <> (BalanceChange.tupled, BalanceChange.unapply)
}
