package persistence

import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolBlock, Share}
import models.DatabaseModels.SPoolBlock
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class PoolBlocksTable(tag: Tag) extends Table[SPoolBlock](tag, "pool_blocks") {
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
  def updated           = column[LocalDateTime]("updated")
  def poolTag           = column[String]("pool_tag")
  def gEpoch            = column[Long]("g_epoch")

  def *                 = (id, blockHeight, netDiff, status, confirmation, effort, nonce, miner,
                           reward, hash, created, poolTag, gEpoch, updated, poolId) <> (SPoolBlock.tupled, SPoolBlock.unapply)
}


