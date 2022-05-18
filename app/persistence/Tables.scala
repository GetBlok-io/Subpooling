package persistence

import slick.lifted.TableQuery

import java.time.LocalDateTime

object Tables {

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
      } yield (shares.miner, shares.difficulty, shares.networkDifficulty, settings.map(_.subpool))
      if(tag != defaultTag)
        joined.filter(_._4 === tag).map(j => j._2 / j._3).sum.result
      else
        joined.filter(j => j._4 === tag || j._4.isEmpty).map(j => j._2 / j._3).sum.result
    }

    def queryBeforeDate(tag: String, defaultTag: String, startDate: LocalDateTime, offset: Long, limit: Long) = {
      import slick.jdbc.PostgresProfile.api._
      val joined = for {
        (shares, settings) <- this.filter(s => s.created <= startDate).drop(offset).take(limit) joinLeft Tables.MinerSettingsTable on (_.miner === _.address)
      } yield (shares.miner, shares.difficulty, shares.networkDifficulty, settings.map(_.subpool))
      if(tag != defaultTag)
        joined.filter(_._4 === tag).result
      else
        joined.filter(j => j._4 === tag || j._4.isEmpty).result
    }

  }
  object MinerSettingsTable extends TableQuery(new MinerSettingsTable(_))
  object SharesArchiveTable extends TableQuery(new SharesArchiveTable(_))
  object MinerStatsArchiveTable extends TableQuery(new MinerStatsArchiveTable(_))

}
