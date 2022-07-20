package io.getblok.subpooling_core
package persistence

import persistence.models.PersistenceModels.{DbConn, PoolPlacement}

import io.getblok.subpooling_core.persistence.models.DataTable

import java.sql.PreparedStatement

class PlacementTable(dbConn: DbConn, part: String) extends DataTable[PoolPlacement](dbConn) {
  override def table: String = "subpool_placements"
  override val numFields: Int = 12

  def queryPlacementsForBlock(block: Long): Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), where, fieldOf("block"), eq, param)
    setLong(1, block)
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }

  def queryPlacementsByGEpoch(gEpoch: Long): Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), where, fieldOf("g_epoch"), eq, param)
    setLong(1, gEpoch)
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }
  /**
  * Deprecated, use query by gEpoch instead
  * */
  @Deprecated
  def queryLastPlacement: Option[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), order, by, fieldOf("block"), desc,
      limit, num(1))
    val rs = execQuery
    if(rs.next())
      Some(PoolPlacement.fromResultSet(rs))
    else
      None
  }

  def queryPoolPlacements: Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part))
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }

  def querySubPoolPlacements(id: Long): Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), where, fieldOf("subpool_id"), eq, param)
    setLong(1, id)
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }

  def querySubPoolPlacementsByBlock(id: Long, block: Long): Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), where, fieldOf("subpool_id"), eq, param,
      and, fieldOf("block"), eq, param)
    setLong(1, id)
    setLong(2, block)
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }

  def queryMinerPendingBalance(miner: String): Long = {
    implicit val ps: PreparedStatement = state(select, sumOf("amount"), fromTable,  where, fieldOf("miner"), eq, param)
    setStr(1, miner)
    val rs = execQuery
    if(rs.next())
      rs.getLong(1)
    else
      0L
  }

  def queryMinerPlacements(miner: String): Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part),  where, fieldOf("miner"), eq, param)
    setStr(1, miner)
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }



  def insertPlacement(poolPlacement: PoolPlacement): Long = {
    implicit val ps: PreparedStatement = state(insert, into, tablePart(part),
      allFields("subpool", "subpool_id", "block", "holding_id", "holding_val", "miner", "score", "minpay",
      "epochs_mined", "amount", "epoch", "g_epoch"), values)
    setStr(1,  poolPlacement.subpool)
    setLong(2, poolPlacement.subpool_id)
    setLong(3, poolPlacement.block)
    setStr(4, poolPlacement.holding_id)
    setLong(5, poolPlacement.holding_val)
    setStr(6, poolPlacement.miner)
    setLong(7, poolPlacement.score)
    setLong(8, poolPlacement.minpay)
    setLong(9, poolPlacement.epochs_mined)
    setLong(10, poolPlacement.amount)
    setLong(11, poolPlacement.epoch)
    setLong(12, poolPlacement.g_epoch)
    execUpdate
  }

  def insertPlacementArray(arr: Array[PoolPlacement]): Long = {
    val rows = for(p <- arr) yield insertPlacement(p)
    rows.sum
  }

  def deleteByBlock(block: Long): Long = {
    implicit val ps: PreparedStatement = state(delete, fromTablePart(part), where, fieldOf("block"), eq, param)
    setLong(1, block)
    execUpdate
  }


}
