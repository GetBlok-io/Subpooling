package persistence

import org.slf4j.{Logger, LoggerFactory}
import slick.lifted.TableQuery

import java.time.LocalDateTime

object Tables {
  private val logger: Logger = LoggerFactory.getLogger("DatabaseTables")
  object Balances extends TableQuery(new BalancesTable(_))

  object BalanceChanges extends TableQuery(new BalanceChangesTable(_))
  object Payments extends TableQuery(new PaymentsTable(_))
  object SubPoolMembers extends TableQuery(new PoolMembersTable(_))
  object MinerStats extends TableQuery(new MinerStatsTable(_))

  object PoolSharesTable extends TableQuery(new PoolSharesTable(_)){

    def getEffortDiff(tag: String, defaultTag: String, lastBlock: Long) = {
      import slick.jdbc.PostgresProfile.api._
      val joined = for {
        (shares, settings) <- this.filter(_.blockHeight > lastBlock) joinLeft Tables.MinerSettingsTable on (_.miner === _.address)
      } yield (shares.miner, shares.difficulty, shares.networkDifficulty, settings.map(_.subpool).flatten)
      if(tag != defaultTag)
        joined.filter(_._4.isDefined).filter(j => j._4.map(_ === tag)).map(j => j._2 / j._3).sum.result
      else
        joined.filter(j => (j._4.isDefined && j._4.map(_ === tag)) || j._4.isEmpty).map(j => j._2 / j._3).sum.result
    }

    def queryBeforeDate(tag: String, defaultTag: String, startDate: LocalDateTime, offset: Long, limit: Long) = {
      import slick.jdbc.PostgresProfile.api._
      val joined = for {
        (shares, settings) <- this.filter(s => s.created <= startDate).drop(offset).take(limit) joinLeft Tables.MinerSettingsTable on (_.miner === _.address)
      } yield (shares.miner, shares.difficulty, shares.networkDifficulty, settings.map(_.subpool).flatten)
      if(tag != defaultTag)
        joined.filter(j => j._4.isDefined).filter(j => j._4.map(_ === tag)).result
      else
        joined.filter(j => j._4.isEmpty || (j._4.isDefined && j._4.map(_ === tag))).result
    }

  }
  object MinerSettingsTable extends TableQuery(new MinerSettingsTable(_))
  object SharesArchiveTable extends TableQuery(new SharesArchiveTable(_))
  object MinerStatsArchiveTable extends TableQuery(new MinerStatsArchiveTable(_))
  object PoolInfoTable extends TableQuery(new PoolInfoTable(_))
  object PoolStatesTable extends TableQuery(new PoolStatesTable(_)) {
/*    def apply(poolTag: String) = {
      TableQuery(new PoolStatesTable(_, "subpool_states_"+poolTag))
    }*/
  }

  def makePartition(tableName: String, part: String) = {
    import slick.jdbc.PostgresProfile.api._
    val totalName = tableName+"_"+part
    logger.info(s"Creating new partition for table $tableName with partition value $part")

    val statement = sqlu"""CREATE TABLE #$totalName PARTITION OF #$tableName FOR VALUES IN ('#$part')"""
    statement.statements.foreach(logger.info)
    statement
  }

  def makePoolPartitions(tag: String) = {
    import slick.jdbc.PostgresProfile.api._
    Seq(
      makePartition("subpool_states", tag),
      makePartition("subpool_members", tag),
      makePartition("subpool_placements", tag),
      makePartition("pool_blocks", tag)
    )
  }

  object PoolBlocksTable extends TableQuery(new PoolBlocksTable(_))
}
