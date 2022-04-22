package io.getblok.subpooling_core
package persistence

import persistence.models.DataTable
import persistence.models.Models.{DbConn, PoolInformation, PoolState}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class InfoTable(dbConn: DbConn) extends DataTable[PoolInformation](dbConn) {
  override def table: String = "pool_info"
  override val numFields: Int = 19

  def queryAllPools: Seq[PoolInformation] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable)
    val rs = execQuery
    buildSeq(rs, PoolInformation.fromResultSet)
  }

  def queryPool(poolTag: String): PoolInformation = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("pool_tag"), eq, param)
    setStr(1, poolTag)
    val rs = execQuery
    rs.next()
    PoolInformation.fromResultSet(rs)
  }

  def queryWithOfficial(official: Boolean): Seq[PoolInformation] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("official"), eq, param)
    setBool(1, official)
    val rs = execQuery
    buildSeq(rs, PoolInformation.fromResultSet)
  }


  def insertNewInfo(poolInformation: PoolInformation): Long = {
    implicit val ps: PreparedStatement = state(insert, into, thisTable,
      allFields("pool_tag", "g_epoch", "subpools", "last_block", "total_members",
        "value_locked", "total_paid", "currency", "payment_type", "fees", "official", "epoch_kick",
        "max_members", "title", "creator", "updated", "created", "emissions_id", "emissions_type", "blocks_found"),values)
    setStr(1, poolInformation.poolTag)
    setLong(2, poolInformation.g_epoch)
    setLong(3, poolInformation.subpools)
    setLong(4, poolInformation.last_block)
    setLong(5, poolInformation.total_members)
    setLong(6, poolInformation.value_locked)
    setLong(7, poolInformation.total_paid)
    setStr(8, poolInformation.currency)
    setStr(9, poolInformation.payment_type)
    setLong(10, poolInformation.fees)
    setBool(11, poolInformation.official)
    setLong(12, poolInformation.epoch_kick)
    setLong(13, poolInformation.max_members)
    setStr(14, poolInformation.title)
    setStr(15, poolInformation.creator)
    setDate(16, poolInformation.updated)
    setDate(17, poolInformation.created)
    setStr(18, poolInformation.emissions_id)
    setStr(19, poolInformation.emissions_type)
    setLong(20, poolInformation.blocksFound)
    execUpdate
  }

  def insertInfoArray(arr: Array[PoolInformation]): Long = {
    val rows = for(m <- arr) yield insertNewInfo(m)
    rows.sum
  }

  def updatePoolInfo(poolTag: String, gEpoch: Long, lastBlock: Long, totalMembers: Long, valueLocked: Long,
                     totalPaid: Long): Long = {
    implicit val ps: PreparedStatement = state(update, thisTable, set,
      fields("g_epoch", "last_block", "total_members", "value_locked", "total_paid",
      "updated"),
      where, "pool_tag", eq, param)

    setLong(1, gEpoch)
    setLong(2, lastBlock)
    setLong(3, totalMembers)
    setLong(4, valueLocked)
    setLong(5, totalPaid)
    setDate(6, LocalDateTime.now())
    setStr(7, poolTag)
    execUpdate
  }

  def updatePoolEmissions(poolTag: String, currency: String, emissionsId: String, emissionsType: String): Long = {
    implicit val ps: PreparedStatement = state(update, thisTable, set,
      fields("currency", "emissions_id", "emissions_type"),
      where, "pool_tag", eq, param)
    setStr(1, currency)
    setStr(2, emissionsId)
    setStr(3, emissionsType)
    setStr(4, poolTag)
    execUpdate
  }

  def updateBlocksFound(poolTag: String, blocksFound: Long): Long = {
    implicit val ps: PreparedStatement = state(update, thisTable, set,
      fieldOf("blocks_found"), eq, param,
      where, "pool_tag", eq, param)
    setLong(1, blocksFound)
    setStr(2, poolTag)
    execUpdate
  }

  def deletePool(poolTag: String): Long = {
    implicit val ps: PreparedStatement = state(delete, fromTable, where, fieldOf("pool_tag"), eq, param)
    setStr(1, poolTag)
    execUpdate
  }


}
