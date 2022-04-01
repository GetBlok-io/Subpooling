package persistence

import persistence.models.DataTable
import persistence.models.Models.{DbConn, PoolMember, PoolPlacement}

import java.sql.PreparedStatement

class PlacementTable(dbConn: DbConn, part: String) extends DataTable[PoolPlacement](dbConn) {
  override val table: String = "subpool_placements"
  override val numFields: Int = 20

  def queryPlacementsForBlock(block: Long): Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), where, fieldOf("block"), eq, param)
    setLong(1, block)
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }

  def queryMinerPendingBalance(miner: String): Long = {
    implicit val ps: PreparedStatement = state(select, sumOf("amount"), fromTablePart(part),  where, fieldOf("miner"), eq, param)
    setStr(1, miner)
    val rs = execQuery
    rs.getLong(1)
  }

  def queryPlacementsForSubpoolAtBlock(id: Long, block: Long): Seq[PoolPlacement] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part),  where, fieldOf("subpool_id"), eq, param,
      and, fieldOf("block"), eq, param)
    setLong(1, id)
    setLong(2, block)
    val rs = execQuery
    buildSeq(rs, PoolPlacement.fromResultSet)
  }


  def insertPlacement(poolPlacement: PoolPlacement): Long = {
    implicit val ps: PreparedStatement = state(insert, into, s"$table ",
      allFields("subpool", "subpool_id", "block", "holding_id", "holding_val", "miner", "score", "minpay",
      "epochs_mined", "amount"), values)
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
