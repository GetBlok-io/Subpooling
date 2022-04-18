package io.getblok.subpooling_core
package persistence

import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models.{DbConn, Share}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class SharesTable(dbConn: DbConn) extends DataTable[Share](dbConn) {
  override def table: String = "shares"
  override val numFields: Int = 6

  def queryNext50k(blockHeight: Long, start: Int = 0): Seq[Share] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("blockheight"), lTeq, param,
      order, by, fieldOf("created"), desc, limit, num(50000), offset, num(start))
    setLong(1, blockHeight)
    val rs = execQuery
    buildSeq(rs, Share.fromResultSet)
  }

  def deleteBeforeCreated(created: LocalDateTime): Long = {
    implicit val ps: PreparedStatement = state(delete, fromTable, where, fieldOf("created"), lT, param)
    setDate(1, created)
    execUpdate
  }







}