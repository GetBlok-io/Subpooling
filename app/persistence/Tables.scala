package persistence

import io.getblok.subpooling_core.persistence.models.PersistenceModels.PoolState
import io.getblok.subpooling_core.states.models.TransformResult
import models.DatabaseModels.StateHistory
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
        this.filter(_.blockHeight > lastBlock).result
    }

    def queryPoolMiners(tag: String, defaultTag: String) = {
      import slick.jdbc.PostgresProfile.api._
      if(tag != defaultTag) {
        Tables.MinerSettingsTable.filter(_.subpool.isDefined).filter(_.subpool === tag).result
      }else{
        Tables.MinerSettingsTable.filter(ms => (ms.subpool.isDefined && ms.subpool === tag) || ms.subpool.isEmpty).result
      }
    }

    def queryMinerPools = {
      import slick.jdbc.PostgresProfile.api._
      Tables.MinerSettingsTable.map(ms => ms.address -> ms.subpool).result
    }

    def queryBeforeDate(startDate: LocalDateTime, offset: Long, limit: Long) = {
      import slick.jdbc.PostgresProfile.api._

      this.filter(s => s.created <= startDate).sortBy(s => s.created.desc).drop(offset).take(limit).result
    }

    def queryBetweenDate(startDate: LocalDateTime, endDate: LocalDateTime, offset: Long, limit: Long) = {
      import slick.jdbc.PostgresProfile.api._

      this.filter(s => s.created >= startDate).filter(s => s.created <= endDate).sortBy(s => s.created.desc).drop(offset).take(limit).result
    }
    def queryMinerSharesBetweenDate(startDate: LocalDateTime, endDate: LocalDateTime, miner: String, offset: Long, limit: Long) = {
      import slick.jdbc.PostgresProfile.api._

      this.filter(s => s.created >= startDate).filter(s => s.created <= endDate).filter(s => s.miner === miner).sortBy(s => s.created.desc).drop(offset).take(limit).result
    }


  }
  object MinerSettingsTable extends TableQuery(new MinerSettingsTable(_))
  object SharesArchiveTable extends TableQuery(new SharesArchiveTable(_))
  object MinerStatsArchiveTable extends TableQuery(new MinerStatsArchiveTable(_))
  object PoolInfoTable extends TableQuery(new PoolInfoTable(_))
  object PoolPlacementsTable extends TableQuery(new PoolPlacementsTable(_))
  object PoolStatesTable extends TableQuery(new PoolStatesTable(_)) {
/*    def apply(poolTag: String) = {
      TableQuery(new PoolStatesTable(_, "subpool_states_"+poolTag))
    }*/
  }
  object StateHistoryTables extends TableQuery(new StateHistoryTable(_)) {
    def fromTransforms(transforms: Seq[TransformResult[_]], gEpoch: Long, block: Long): Seq[StateHistory] = {
      val histories = transforms.map{
        t =>
          val poolTag = t.nextState.poolTag
          val box = t.nextState.box.getId.toString
          val tx = t.id
          val commandBox = t.commandState.box.getId.toString
          val command = t.command.toString
          val step = t.step
          val digest = t.manifest.map(_.digestString).getOrElse("none")
          val manifest = t.manifest.map(_.manifestString).getOrElse("none")
          val subTrees = t.manifest.map(_.subtreeStrings).getOrElse(Seq("none", "none", "none", "none"))

          StateHistory(poolTag, gEpoch, box, tx, commandBox, command, PoolState.SUCCESS, step, digest, manifest,
            subTrees.applyOrElse(0, (a: Int) => "none"), subTrees.applyOrElse(1, (a: Int) => "none"),
            subTrees.applyOrElse(2, (a: Int) => "none"), subTrees.applyOrElse(3, (a: Int) => "none"),
            block, LocalDateTime.now(), LocalDateTime.now())
      }
      histories
    }

  }

  object PoolBalanceStateTable extends TableQuery(new PoolBalanceStateTable(_)) {

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
  object BlocksTable extends TableQuery(new BlocksTable(_))
}
