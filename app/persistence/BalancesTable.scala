package persistence

import io.getblok.subpooling_core.persistence.models.PersistenceModels.{Block, DbConn}
import models.DatabaseModels.Balance
import slick.lifted.Tag
import slick.jdbc.PostgresProfile.api._

import java.sql.PreparedStatement
import java.time.LocalDateTime

class BalancesTable(tag: Tag) extends Table[Balance](tag, "balances") {
  def poolId       = column[String]("poolid", O.PrimaryKey)
  def address      = column[String]("address", O.PrimaryKey)
  def amount       = column[Double]("amount")
  def created      = column[LocalDateTime]("created")
  def updated      = column[LocalDateTime]("updated")
  def *            = (poolId, address, amount, created, updated) <> (Balance.tupled, Balance.unapply)
}


