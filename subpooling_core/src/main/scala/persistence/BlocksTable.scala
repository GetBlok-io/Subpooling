package io.getblok.subpooling_core
package persistence

import persistence.models.Models.{Block, DbConn}

import io.getblok.subpooling_core.persistence.models.DataTable

import java.sql.PreparedStatement
class BlocksTable(dbConn: DbConn) extends DataTable[Block](dbConn) {
  override def table: String = "blocks"
  override val numFields: Int = 14

  def queryByHeight(height: Long): Block = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("blockheight"), eq, param)
    setLong(1, height)
    val rs = execQuery
    rs.next()
    Block.fromResultSet(rs)
  }

  def queryPendingBlocks(maxRows: Int): Seq[Block] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"), limit, param)
    setStr(1, Block.PENDING)
    setInt(2, maxRows)
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

  def queryByStatus(status: String): Seq[Block] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, status)
    val rs = execQuery
    buildSeq(rs, Block.fromResultSet)
  }
}
