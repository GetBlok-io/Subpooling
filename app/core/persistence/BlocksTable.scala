package io.getblok.subpooling
package core.persistence

import java.sql.PreparedStatement
import models.DataTable
import models.Models._
class BlocksTable(dbConn: DbConn) extends DataTable[Block](dbConn) {
  override val table: String = "blocks"
  override val numFields: Int = 14

  def queryByHeight(height: Long): Block = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("blockheight"), eq, param)
    setLong(1, height)
    val rs = execQuery
    rs.next()
    Block.fromResultSet(rs)
  }

  def queryPendingBlocks: Seq[Block] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, Block.PENDING)
    val rs = execQuery
    buildSeq(rs, Block.fromResultSet)
  }

  def queryConfirmedBlocks: Seq[Block] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, Block.CONFIRMED)
    val rs = execQuery
    buildSeq(rs, Block.fromResultSet)
  }

  def queryInitiatedBlocks: Seq[Block] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, Block.INITIATED)
    val rs = execQuery
    buildSeq(rs, Block.fromResultSet)
  }

  def updateBlockStatus(status: String, height: Long): Long = {
    implicit val ps: PreparedStatement = state(update, thisTable, set, fieldOf("status"), eq, param, where,
      fieldOf("blockheight"), eq, param)
    setStr(1, status)
    setLong(2, height)
    execUpdate
  }




}
