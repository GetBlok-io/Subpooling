package persistence

import models.DatabaseModels.{SBlock, SPoolBlock}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class BlocksTable(tag: Tag) extends Table[SBlock](tag, "blocks") {
  def id                = column[Long]("id")
  def poolId            = column[String]("poolid")
  def blockHeight       = column[Long]("blockheight")
  def netDiff           = column[Double]("networkdifficulty")
  def status            = column[String]("status")
  def confirmation      = column[Double]("confirmationprogress")
  def effort            = column[Option[Double]]("effort")
  def nonce             = column[Option[String]]("transactionconfirmationdata")
  def miner             = column[String]("miner")
  def reward            = column[Double]("reward")
  def hash              = column[Option[String]]("hash")
  def created           = column[LocalDateTime]("created")
  def *                 = (id, blockHeight, netDiff, status, confirmation, effort, nonce, miner,
                           reward, hash, created) <> (SBlock.tupled, SBlock.unapply)
}


