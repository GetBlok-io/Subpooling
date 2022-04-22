package io.getblok.subpooling_core
package persistence

import node.NodeHandler
import node.NodeHandler.PartialBlockInfo
import persistence.models.DataTable
import persistence.models.Models._

import java.sql.PreparedStatement
import java.time.LocalDateTime

class PoolBlocksTable(dbConn: DbConn) extends DataTable[PoolBlock](dbConn) {
  override def table: String = "pool_blocks"
  override val numFields: Int = 16

  def queryByHeight(height: Long): PoolBlock = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("blockheight"), eq, param)
    setLong(1, height)
    val rs = execQuery
    rs.next()
    PoolBlock.fromResultSet(rs)
  }

  def queryValidatingBlocks: Seq[PoolBlock] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, PoolBlock.VALIDATING)
    val rs = execQuery
    buildSeq(rs, PoolBlock.fromResultSet)
  }

  def queryConfirmedBlocks: Seq[PoolBlock] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, PoolBlock.CONFIRMED)
    val rs = execQuery
    buildSeq(rs, PoolBlock.fromResultSet)
  }

  def queryInitiatedBlocks: Seq[PoolBlock] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, PoolBlock.CONFIRMING)
    val rs = execQuery
    buildSeq(rs, PoolBlock.fromResultSet)
  }

  def updateBlockStatus(status: String, height: Long): Long = {
    implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "updated"), where,
      fieldOf("blockheight"), eq, param)
    setStr(1, status)
    setDate(2, LocalDateTime.now())
    setLong(3, height)
    execUpdate
  }
  def updateBlockStatusAndConfirmation(status: String, confirmation: Double, height: Long, gEpoch: Long = -1): Long = {
    implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "confirmationprogress", "g_epoch", "updated"), where,
      fieldOf("blockheight"), eq, param)
    setStr(1, status)
    setDec(2, confirmation)
    setLong(3, gEpoch)
    setDate(4, LocalDateTime.now())
    setLong(5, height)
    execUpdate
  }
  def updateBlockValidation(height: Long, partialBlockInfo: PartialBlockInfo): Long = {
    partialBlockInfo match {
      case NodeHandler.ValidBlock(reward, txConf, hash) =>
        implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "reward",
          "transactionconfirmationdata", "hash", "updated"), where, fieldOf("blockheight"), eq, param)
        setStr(1, PoolBlock.CONFIRMING)
        setDec(2, reward)
        setStr(3, txConf)
        setStr(4, hash)
        setDate(5, LocalDateTime.now())
        setLong(6, height)
        execUpdate
      case NodeHandler.ConfirmedBlock(reward, txConf, hash, gEpoch) =>
        implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "confirmationprogress", "reward",
          "transactionconfirmationdata", "hash", "gEpoch", "updated"), where, fieldOf("blockheight"), eq, param)
        setStr(1, PoolBlock.CONFIRMED)
        setDec(2, 1.0)
        setDec(3, reward)
        setStr(4, txConf)
        setStr(5, hash)
        setLong(6, gEpoch)
        setDate(7, LocalDateTime.now())
        setLong(8, height)
        execUpdate
      case NodeHandler.OrphanBlock(reward, txConf, hash) =>
        implicit val ps: PreparedStatement = state(update, thisTable, set, fields("status", "reward",
          "transactionconfirmationdata", "hash", "updated"), where, fieldOf("blockheight"), eq, param)
        setStr(1, PoolBlock.ORPHANED)
        setDec(2, reward)
        setStr(3, txConf)
        setStr(4, hash)
        setDate(5, LocalDateTime.now())
        setLong(6, height)
        execUpdate
    }

  }

  def insertWithBlock(blockHeight: Long, poolTag: String): Long = {
    implicit val ps: PreparedStatement = state(insert, into, tablePart(poolTag), "VALUES", valueOfFields("id", "poolid",
    "blockheight", "networkdifficulty", "status", "type", "confirmationprogress", "effort", "transactionconfirmationdata",
    "miner", "reward", "source", "hash", "created", "pool_tag", "g_epoch", "updated"), "\n", "(", select, all, fromTableOf("blocks"),
      where, fieldOf("blockheight"), eq, param, ", ", param, ", ", param, ", ", param, ")")
    setLong(1, blockHeight)
    setStr(2, poolTag)
    setLong(3, -1L)
    setDate(4, LocalDateTime.now())
    execUpdate
  }

  def queryByStatus(status: String): Seq[PoolBlock] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("status"), eq, param,
      order, by, fieldOf("created"))
    setStr(1, status)
    val rs = execQuery
    buildSeq(rs, PoolBlock.fromResultSet)
  }

  def queryById(id: Long): PoolBlock = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("id"), eq, param)
    setLong(1, id)
    val rs = execQuery
    rs.next()
    PoolBlock.fromResultSet(rs)
  }




}
