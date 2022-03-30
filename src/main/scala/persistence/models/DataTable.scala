package persistence.models

import persistence.models.Models.DbConn

import java.sql.{PreparedStatement, ResultSet}
import java.time.LocalDateTime
import java.util.Date

abstract class DataTable[T](dbConn: DbConn) {
  val table: String
  val numFields: Int

  val select:          String = "SELECT "
  val all:             String = "* "
  val fromTable:       String = s"FROM $table "
  val where:           String = "WHERE "
  val update:          String = "UPDATE "
  val set:             String = "SET "
  val and:             String = "AND "
  val eq:              String = "= "
  val param:           String = "? "
  val delete:          String = "DELETE "
  val insert:          String = "INSERT "
  val into:            String = "INTO "
  val fetch:           String = "FETCH "
  val next:            String = "NEXT "
  val rows:            String = "ROWS "
  val only:            String = "ONLY "
  val order:           String = "ORDER "
  val by:              String = "BY "
  val limit:           String = "LIMIT "
  val desc:      String = "DESC "

  def values:      String = {
    val prefix = "VALUES(?"
    val fields: Seq[String] = for(i <- 1 until numFields) yield ", ?"
    val suffix = ") "
    concat(Seq(prefix, concat(fields), suffix))
  }

  def fromTablePart(part: String): String = {
    s"FROM ${table}_$part "
  }

  def tablePart(part: String): String = {
    s"${table}_$part "
  }


  def allFields(fieldSeq: String*): String = {
    val prefix = s"$table ("
    val fields = for(i <- 0 until numFields - 1) yield fieldSeq(i) + ", "
    val fieldStr = concat(fields) + fieldSeq(numFields - 1)
    val suffix = ") "
    concat(Seq(prefix, fieldStr, suffix))
  }

  def fields(fieldSeq: String*): String = {
    val fields = for(i <- 0 until fieldSeq.length - 1) yield fieldSeq(i) + " = ?, "
    val fieldStr = concat(fields) + fieldSeq(fieldSeq.length - 1) + " = ? "
    fieldStr
  }

  def fieldOf(field: String): String = {
    s"$field "
  }

  def sumOf(field: String): String = {
    s"sum($field) "
  }

  def num(int: Int): String = {
    s"$int "
  }

  def concat(statements: Seq[String]): String = {
    statements.reduce((s1, s2) => s1 + s2)
  }
  def state(statements: String*): PreparedStatement = {
    dbConn.state(concat(statements))
  }
  def setStr(idx: Int, str: String)(implicit ps: PreparedStatement): Unit = {
    ps.setString(idx, str)
  }
  def setLong(idx: Int, lng: Long)(implicit ps: PreparedStatement): Unit  = {
    ps.setLong(idx, lng)
  }
  def setDec(idx: Int, dec: Double)(implicit ps: PreparedStatement): Unit = {
    ps.setDouble(idx, dec)
  }
  def setInt(idx: Int, int: Int)(implicit ps: PreparedStatement): Unit = {
    ps.setInt(idx, int)
  }
  def setDate(idx: Int, dte: LocalDateTime)(implicit ps: PreparedStatement): Unit = {
    ps.setObject(idx, dte)
  }
  def execUpdate(implicit ps: PreparedStatement): Long = {
    val rows = ps.executeUpdate()
    ps.close()
    rows
  }
  def execQuery(implicit ps: PreparedStatement): ResultSet = {
    ps.executeQuery()
  }
  def buildSeq(rs: ResultSet, f: ResultSet => T): Seq[T] = {
    var arr = Seq[T]()
    while(rs.next()){
      arr = arr ++ Seq(f(rs))
    }
    arr
  }

}
