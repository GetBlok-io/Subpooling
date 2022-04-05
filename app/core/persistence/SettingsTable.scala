package io.getblok.subpooling
package core.persistence

import core.persistence.models.Models.{DbConn, MinerSettings}

import core.persistence.models.DataTable

import java.sql.PreparedStatement

class SettingsTable(dbConn: DbConn) extends DataTable[MinerSettings](dbConn) {
  override val table: String = "miner_settings"
  override val numFields: Int = 6

  def queryByMiner(miner: String): MinerSettings = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("address"), eq, param)
    setStr(1, miner)
    val rs = execQuery
    rs.next()
    MinerSettings.fromResultSet(rs)
  }

  def queryBySubpool(subpool: String): Seq[MinerSettings] = {
    implicit val ps: PreparedStatement = state(select, all, fromTable, where, fieldOf("subpool"), eq, param)
    setStr(1, subpool)
    val rs = execQuery
    rs.next()
    buildSeq(rs, MinerSettings.fromResultSet)
  }







}
