package io.getblok.subpooling_core
package persistence

import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models.{DbConn, MinerSettings}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class SettingsTable(dbConn: DbConn) extends DataTable[MinerSettings](dbConn) {
  override def table: String = "miner_settings"
  override val numFields: Int = 7

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
    buildSeq(rs, MinerSettings.fromResultSet)
  }

  def updateMinerPool(address: String, subpool: String): Long = {
    implicit val ps: PreparedStatement = state(insert, into, thisTable,
      allFields("poolid", "address", "paymentthreshold", "created", "updated", "donation", "subpool"),
      values, onConflict, valueOfFields("poolid", "address"), doUpdate, set, fieldOf("subpool"), eq, param)

    val currentTime = LocalDateTime.now()
    // TODO: Link app params to config
    setStr(1, AppParameters.mcPoolId)
    setStr(2, address)
    setDec(3, Helpers.nanoErgToErg(AppParameters.defaultMinPay))
    setDate(4, currentTime)
    setDate(5, currentTime)
    setStr(6, "none")
    setStr(7, subpool)
    setStr(8, subpool)
    execUpdate
  }

  def updateMinerPaymentThreshold(address: String, paymentThreshold: Double): Long = {
    implicit val ps: PreparedStatement = state(insert, into, thisTable,
      allFields("poolid", "address", "paymentthreshold", "created", "updated", "donation", "subpool"),
      values, onConflict, valueOfFields("poolid", "address"), doUpdate, set, fieldOf("paymentthreshold"), eq, param)

    val currentTime = LocalDateTime.now()
    // TODO: Link app params to config
    setStr(1, AppParameters.mcPoolId)
    setStr(2, address)
    setDec(3, Helpers.nanoErgToErg(AppParameters.defaultMinPay))
    setDate(4, currentTime)
    setDate(5, currentTime)
    setStr(6, "none")
    setStr(7, "none")
    setDec(8, paymentThreshold)
    execUpdate
  }







}
