package io.getblok.subpooling_core
package persistence

import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models.{DbConn, PoolState}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class StateTable(dbConn: DbConn) extends DataTable[PoolState](dbConn) {
  override def table: String = "subpool_states"
  override val numFields: Int = 17

  def queryAllSubPoolStates(part: String): Seq[PoolState] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part))
    val rs = execQuery
    buildSeq(rs, PoolState.fromResultSet)
  }

  def queryAllPoolStates: Seq[PoolState] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable)

    val rs = execQuery
    buildSeq(rs, PoolState.fromResultSet)
  }

  def queryPoolStatesAtId(id: Long): Seq[PoolState] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("subpool_id"), eq, param)
    setLong(1, id)
    val rs = execQuery
    buildSeq(rs, PoolState.fromResultSet)
  }

  def querySubpoolState(part: String, id: Long): PoolState = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), where, fieldOf("subpool_id"), eq, param)
    setLong(1, id)
    val rs = execQuery
    rs.next()
    PoolState.fromResultSet(rs)
  }

  def querySubpoolStateByEpoch(part: String, id: Long, epoch: Long): PoolState = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part), where, fieldOf("subpool_id"), eq, param, and,
      fieldOf("epoch"), eq, param)
    setLong(1, id)
    setLong(2, epoch)
    val rs = execQuery
    rs.next()
    PoolState.fromResultSet(rs)
  }

  def insertNewState(poolState: PoolState): Long = {


    implicit val ps: PreparedStatement = state(insert, into, tablePart(poolState.subpool),
      allFields("subpool", "subpool_id", "title", "box", "tx", "g_epoch", "epoch", "g_height", "height", "status",
        "members", "block", "creator", "stored_id", "stored_val", "updated", "created"),values)
    setStr(1, poolState.subpool)
    setLong(2, poolState.subpool_id)
    setStr(3, poolState.title)
    setStr(4, poolState.box)
    setStr(5, poolState.tx)
    setLong(6, poolState.g_epoch)
    setLong(7, poolState.epoch)
    setLong(8, poolState.g_height)
    setLong(9, poolState.height)
    setStr(10, poolState.status)
    setInt(11, poolState.members)
    setLong(12, poolState.block)
    setStr(13, poolState.creator)
    setStr(14, poolState.stored_id)
    setLong(15, poolState.stored_val)
    setDate(16, poolState.updated)
    setDate(17, poolState.created)
    execUpdate
  }

  def insertStateArray(arr: Array[PoolState]): Long = {
    val rows = for(m <- arr) yield insertNewState(m)
    rows.sum
  }

  /**
   * Called every time a pool enters INITIATED status. Affects all subPools in the Pool
   */
  def updateGEpoch(part: String, gepoch: Long): Long = {
    implicit val ps: PreparedStatement = state(update, tablePart(part), set, fields("g_epoch", "updated"))
    setLong(1, gepoch)
    setDate(2, LocalDateTime.now())
    execUpdate
  }

  /**
   * Update this subPool's state to SUCCESS. Only tx, height, and status are changed
   */
  def updateSuccess(part: String, id: Long, tx: String, height: Long): Long = {
    implicit val ps: PreparedStatement = state(update, tablePart(part), set,
      fields( "tx", "height", "status", "updated"), where, "subpool_id ", eq, param)
    setStr(1, tx)
    setLong(2, height)
    setStr(3, PoolState.SUCCESS)
    setDate(4, LocalDateTime.now())
    setLong(5, id)
    execUpdate
  }
  /**
   * Update this subPool's state to CONFIRMED. Only box, stored_id, stored_val, and status are changed
   */
  def updateConfirmed(part: String, id: Long, box: String, stored_id: String, stored_val: Long): Long = {
    implicit val ps: PreparedStatement = state(update, tablePart(part), set,
      fields( "box", "stored_id", "stored_val", "status", "updated"), where, "subpool_id ", eq, param)
    setStr(1, box)
    setStr(2, stored_id)
    setLong(3, stored_val)
    setStr(4, PoolState.CONFIRMED)
    setDate(5, LocalDateTime.now())
    setLong(6, id)
    execUpdate
  }

  /**
   * Update this subPool's state to INITIATED. Only epoch, members, status, and block are changed
   */
  def updateInitiated(part: String, id: Long, epoch: Long, members: Int, block: Long): Long = {
    implicit val ps: PreparedStatement = state(update, tablePart(part), set,
      fields( "epoch", "members", "block", "status", "updated"), where, "subpool_id ", eq, param)
    setLong(1, epoch)
    setInt(2, members)
    setLong(3, block)
    setStr(4, PoolState.INITIATED)
    setDate(5, LocalDateTime.now())
    setLong(6, id)
    execUpdate
  }
  /**
   * Update this subPool's state to FAILURE. Only height and status are changed
   */
  def updateFailure(part: String, id: Long, height: Long): Long = {
    implicit val ps: PreparedStatement = state(update, tablePart(part), set,
      fields( "height", "status", "updated"), where, "subpool_id ", eq, param)
    setLong(1, height)
    setStr( 2, PoolState.FAILURE)
    setDate(3, LocalDateTime.now())
    setLong(4, id)
    execUpdate
  }

  def updatePoolState(poolState: PoolState): Long = {
    implicit val ps: PreparedStatement = state(update, tablePart(poolState.subpool), set,
      fields("box", "tx", "epoch", "height", "status", "members", "block", "stored_id", "stored_val", "updated"),
      where, "subpool_id", eq, param)

    setStr(1, poolState.box)
    setStr(2, poolState.tx)
    setLong(3, poolState.epoch)
    setLong(4, poolState.height)
    setStr(5, poolState.status)
    setInt(6, poolState.members)
    setLong(7, poolState.block)
    setStr(8, poolState.stored_id)
    setLong(9, poolState.stored_val)
    setDate(10, poolState.updated)
    setLong(11, poolState.subpool_id)
    execUpdate
  }

  def updateManyStates(poolStates: Seq[PoolState]): Long = {
    poolStates.map(updatePoolState).sum
  }


  def deletePool(part: String): Long = {
    implicit val ps: PreparedStatement = state(delete, fromTablePart(part), where, fieldOf("subpool"), eq, param)
    setStr(1, part)
    execUpdate
  }


}
