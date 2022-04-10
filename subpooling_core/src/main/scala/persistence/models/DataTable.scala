package io.getblok.subpooling_core
package persistence.models

import persistence.models.Models.DbConn

import org.slf4j.{Logger, LoggerFactory}

import java.sql.{PreparedStatement, ResultSet}
import java.time.LocalDateTime

abstract class DataTable[T](dbConn: DbConn) {
  protected def table: String
  protected val numFields: Int

  protected val select:          String = "SELECT "
  protected val all:             String = "* "
  protected def fromTable:       String = s"FROM $table "
  protected def thisTable:       String = s"$table "
  protected val where:           String = "WHERE "
  protected val update:          String = "UPDATE "
  protected val set:             String = "SET "
  protected val and:             String = "AND "
  protected val eq:              String = "= "
  protected val param:           String = "? "
  protected val delete:          String = "DELETE "
  protected val insert:          String = "INSERT "
  protected val into:            String = "INTO "
  protected val fetch:           String = "FETCH "
  protected val next:            String = "NEXT "
  protected val rows:            String = "ROWS "
  protected val only:            String = "ONLY "
  protected val order:           String = "ORDER "
  protected val by:              String = "BY "
  protected val limit:           String = "LIMIT "
  protected val offset:          String = "OFFSET "
  protected val desc:            String = "DESC "
  protected val gT:              String = "> "
  protected val lT:              String = "< "
  protected val gTeq:            String = ">= "
  protected val lTeq:            String = "<= "
  protected val onConflict:      String = "ON CONFLICT "
  protected val doUpdate:        String = "DO UPDATE "

  protected def values:      String = {
    val prefix = "VALUES(?"
    val fields: Seq[String] = for(i <- 1 until numFields) yield ", ?"
    val suffix = ") "
    concat(Seq(prefix, concat(fields), suffix))
  }

  protected def fromTablePart(part: String): String = {
    s"FROM ${table}_$part "
  }

  protected def tablePart(part: String): String = {
    s"${table}_$part "
  }


  protected def allFields(fieldSeq: String*): String = {
    val prefix = s"("
    val fields = for(i <- 0 until numFields - 1) yield fieldSeq(i) + ", "
    val fieldStr = concat(fields) + fieldSeq(numFields - 1)
    val suffix = ") "
    concat(Seq(prefix, fieldStr, suffix))
  }

  protected def fields(fieldSeq: String*): String = {
    val fields = for(i <- 0 until fieldSeq.length - 1) yield fieldSeq(i) + " = ?, "
    val fieldStr = concat(fields) + fieldSeq(fieldSeq.length - 1) + " = ? "
    fieldStr
  }

  protected def fieldOf(field: String): String = {
    s"$field "
  }

  protected def valueOfFields(fieldSeq: String*): String = {
    val prefix = s"("
    val fields = for(i <- 0 until fieldSeq.length - 1) yield fieldSeq(i) + ", "
    val fieldStr = concat(fields) + fieldSeq(fieldSeq.length - 1)
    val suffix = ") "
    concat(Seq(prefix, fieldStr, suffix))
  }

  protected def sumOf(field: String): String = {
    s"sum($field) "
  }

  protected def num(int: Int): String = {
    s"$int "
  }

  protected def concat(statements: Seq[String]): String = {
    statements.reduce((s1, s2) => s1 + s2)
  }
  protected def state(statements: String*): PreparedStatement = {
    val log: Logger = LoggerFactory.getLogger("DataTable")
    log.error("Executing statement: \"" + concat(statements) + "\"")
    dbConn.state(concat(statements))
  }
  protected def setStr(idx: Int, str: String)(implicit ps: PreparedStatement): Unit = {
    ps.setString(idx, str)
  }
  protected def setLong(idx: Int, lng: Long)(implicit ps: PreparedStatement): Unit  = {
    ps.setLong(idx, lng)
  }
  protected def setDec(idx: Int, dec: Double)(implicit ps: PreparedStatement): Unit = {
    ps.setDouble(idx, dec)
  }
  protected def setInt(idx: Int, int: Int)(implicit ps: PreparedStatement): Unit = {
    ps.setInt(idx, int)
  }
  protected def setDate(idx: Int, dte: LocalDateTime)(implicit ps: PreparedStatement): Unit = {
    ps.setObject(idx, dte)
  }
  protected def execUpdate(implicit ps: PreparedStatement): Long = {
    val rows = ps.executeUpdate()
    ps.close()
    rows
  }
  protected def execQuery(implicit ps: PreparedStatement): ResultSet = {
    ps.executeQuery()
  }
  protected def buildSeq(rs: ResultSet, f: ResultSet => T): Seq[T] = {
    var arr = Seq[T]()
    while(rs.next()){
      arr = arr ++ Seq(f(rs))
    }
    arr
  }

}

object DataTable {
  private val logger = LoggerFactory.getLogger("DataTable")

  def createSubpoolPartitions(dbConn: DbConn, part: String): Int = {
    var rowsUpdated = createNewPartition(dbConn, "subpool_states", part)
    rowsUpdated = createNewPartition(dbConn, "subpool_members", part)
    rowsUpdated = createNewPartition(dbConn, "subpool_placements", part)

    logger.info(s"Partitions created, $rowsUpdated results returned.")
    rowsUpdated
  }

  def createNewPartition(dbConn:DbConn, tableName: String, part: String): Int = {

    logger.info(s"Creating new partition for $tableName with value $part")

    implicit val ps: PreparedStatement = formStatement(dbConn,"CREATE ", "TABLE ", s"${tableName}_$part ", "PARTITION ", "OF ", s"$tableName ",
      "FOR ", "VALUES ", "IN ", s"(\'$part\')")
    ps.executeUpdate()
  }

  private def formStatement(dbConn: DbConn, statements: String*): PreparedStatement = {
    dbConn.state(statements.reduce((s1, s2) => s1 + s2))
  }
}
