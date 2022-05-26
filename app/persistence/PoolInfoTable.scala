package persistence

import io.getblok.subpooling_core.persistence.models.Models.{PoolInformation, PoolMember}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.LocalDateTime

class PoolInfoTable(tag: Tag) extends Table[PoolInformation](tag, "pool_info") {

  def poolTag       = column[String]("pool_tag")
  def gEpoch        = column[Long]("g_epoch")
  def subpools      = column[Long]("subpools")
  def lastBlock     = column[Long]("last_block")
  def totalMembers  = column[Long]("total_members")
  def valueLocked   = column[Long]("value_locked")
  def totalPaid     = column[Long]("total_paid")
  def currency      = column[String]("currency")
  def paymentType   = column[String]("payment_type")
  def fees          = column[Long]("fees")
  def official      = column[Boolean]("official")
  def epochKick     = column[Long]("epoch_kick")
  def maxMembers    = column[Long]("max_members")
  def title         = column[String]("title")
  def creator       = column[String]("creator")
  def updated       = column[LocalDateTime]("updated")
  def created       = column[LocalDateTime]("created")
  def emissionsId   = column[String]("emissions_id")
  def emissionsType = column[String]("emissions_type")
  def blocksFound   = column[Long]("blocks_found")

  def * =   (poolTag, gEpoch, subpools, lastBlock, totalMembers, valueLocked, totalPaid,
              currency, paymentType, fees, official, epochKick, maxMembers, title, creator,
              updated, created, emissionsId, emissionsType, blocksFound) <> (PoolInformation.tupled, PoolInformation.unapply)
}
