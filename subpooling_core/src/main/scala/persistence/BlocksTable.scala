package io.getblok.subpooling_core
package persistence

import persistence.models.DataTable
import persistence.models.Models._

import io.getblok.subpooling_core.node.NodeHandler
import io.getblok.subpooling_core.node.NodeHandler.PartialBlockInfo

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
  def updateBlockStatusAndConfirmation(status: String, confirmation: Double, height: Long): Long = {
    implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "confirmationprogress"), where,
      fieldOf("blockheight"), eq, param)
    setStr(1, status)
    setDec(2, confirmation)
    setLong(3, height)
    execUpdate
  }
  def updateBlockValidation(height: Long, partialBlockInfo: PartialBlockInfo): Long = {
    partialBlockInfo match {
      case NodeHandler.ValidBlock(reward, txConf, hash) =>
        implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "reward",
          "transactionconfirmationdata", "hash"), where, fieldOf("blockheight"), eq, param)
        setStr(1, Block.INITIATED)
        setDec(2, reward)
        setStr(3, txConf)
        setStr(4, hash)
        setLong(5, height)
        execUpdate
      case NodeHandler.ConfirmedBlock(reward, txConf, hash) =>
        implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "confirmationprogress", "reward",
          "transactionconfirmationdata", "hash"), where, fieldOf("blockheight"), eq, param)
        setStr(1, Block.CONFIRMED)
        setDec(2, 1.0)
        setDec(3, reward)
        setStr(4, txConf)
        setStr(5, hash)
        setLong(6, height)
        execUpdate
      case NodeHandler.OrphanBlock(reward, txConf, hash) =>
        implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "reward",
          "transactionconfirmationdata", "hash"), where, fieldOf("blockheight"), eq, param)
        setStr(1, Block.ORPHANED)
        setDec(2, reward)
        setStr(3, txConf)
        setStr(4, hash)
        setLong(5, height)
        execUpdate
    }

  }

  def queryByStatus(status: String): Seq[Block] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, status)
    val rs = execQuery
    buildSeq(rs, Block.fromResultSet)
  }

  def queryById(id: Long): Block = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("id"), eq, param)
    setLong(1, id)
    val rs = execQuery
    rs.next()
    Block.fromResultSet(rs)
  }




}
