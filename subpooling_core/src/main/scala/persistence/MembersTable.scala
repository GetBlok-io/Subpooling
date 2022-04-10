package io.getblok.subpooling_core
package persistence

import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models.{DbConn, PoolMember}

import java.sql.PreparedStatement

class MembersTable(dbConn: DbConn, part: String) extends DataTable[PoolMember](dbConn) {
  override def table: String = "subpool_members"
  override val numFields: Int = 20

  def queryPoolMembersAtGEpoch(gepoch: Long): Seq[PoolMember] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part),  where, "g_epoch ", eq, param)
    setLong(1, gepoch)
    val rs = execQuery
    buildSeq(rs, PoolMember.fromResultSet)
  }

  def querySubPoolMembersAtGEpoch(id: Long, gepoch: Long): Seq[PoolMember] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part),  where, "g_epoch ", eq, param, and, "subpool_id ", eq, param)
    setLong(1, gepoch)
    setLong(2, id)
    val rs = execQuery
    buildSeq(rs, PoolMember.fromResultSet)
  }
  def querySubPoolMembersAtBlock(id: Long, block: Long): Seq[PoolMember] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part),  where, fieldOf("id"), eq, param, and,
     fieldOf("block"), eq, param)
    setLong(1, id)
    setLong(2, block)
    val rs = execQuery
    buildSeq(rs, PoolMember.fromResultSet)
  }

  def querySubPoolMembersAtEpoch(id: Long, epoch: Long): Seq[PoolMember] = {
    implicit val ps: PreparedStatement = state(select, all, fromTablePart(part),  where, "epoch ", eq, param, and, "subpool_id ", eq, param)
    setLong(1, epoch)
    setLong(2, id)
    val rs = execQuery
    buildSeq(rs, PoolMember.fromResultSet)
  }

  def queryMinerCurrentStored(miner: String): Long = {
    implicit val ps: PreparedStatement = state(select, fieldOf("stored"), fromTablePart(part),
      where, fieldOf("miner"), eq, param, order, by, fieldOf("created"), desc, limit, num(1))
    setStr(1, miner)
    val rs = execQuery
    rs.getLong(1)
  }


  def insertMember(member: PoolMember): Long = {
    implicit val ps: PreparedStatement = state(insert, into, s"$table ",
      allFields("subpool", "subpool_id", "tx", "box", "g_epoch", "epoch", "height", "miner", "share_score", "share",
        "share_perc", "minpay", "stored", "paid", "change", "epochs_mined", "token", "token_paid", "block", "created"),values)
    setStr(1, member.subpool)
    setLong(2, member.subpool_id)
    setStr(3, member.tx)
    setStr(4, member.box)
    setLong(5, member.g_epoch)
    setLong(6, member.epoch)
    setLong(7, member.height)
    setStr(8, member.miner)
    setLong(9, member.share_score)
    setLong(10, member.share)
    setDec(11, member.share_perc)
    setLong(12, member.minpay)
    setLong(13, member.stored)
    setLong(14, member.paid)
    setLong(15, member.change)
    setLong(16, member.epochs_mined)
    setStr(17, member.token)
    setLong(18, member.token_paid)
    setLong(19, member.block)
    setDate(20, member.created)
    execUpdate
  }

  def insertMemberArray(arr: Array[PoolMember]): Long = {
    val rows = for(m <- arr) yield insertMember(m)
    rows.sum
  }


}
