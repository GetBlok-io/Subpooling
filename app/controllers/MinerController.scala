
package controllers

import _root_.io.swagger.annotations._
import actors.BlockingDbWriter.{InsertNewPoolInfo, UpdatePoolInfo}
import actors.QuickDbReader._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import io.getblok.subpooling_core.contracts.MetadataContract
import io.getblok.subpooling_core.contracts.emissions.EmissionsContract
import io.getblok.subpooling_core.contracts.holding.TokenHoldingContract
import io.getblok.subpooling_core.global.Helpers
import io.getblok.subpooling_core.groups.builders.GenesisBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.EmptySelector
import io.getblok.subpooling_core.groups.{GenesisGroup, GroupManager}
import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models._
import io.getblok.subpooling_core.registers.PoolFees
import models.DatabaseModels.BalanceChange
import models.ResponseModels.Intervals.{DAILY, MONTHLY, YEARLY}
import models.ResponseModels.{writesMinerResponse, _}
import org.ergoplatform.appkit._
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import slick.jdbc.{JdbcProfile, PostgresProfile}

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Named, Singleton}
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Try

@Api(value = "/miners", description = "Miner Operations")
@Singleton
class MinerController @Inject()(@Named("quick-db-reader") query: ActorRef,
                                components: ControllerComponents, system: ActorSystem, config: Configuration,
                                override protected val dbConfigProvider: DatabaseConfigProvider
                                ) extends SubpoolBaseController(components, config) with HasDatabaseConfigProvider[PostgresProfile]
                                {
  val log: Logger = Logger("MinerController")
  implicit val quickQueryContext: ExecutionContext = system.dispatchers.lookup("subpool-contexts.quick-query-dispatcher")
  implicit val timeOut: Timeout = Timeout(15 seconds)

  import dbConfig.profile.api._

  def getMiner(address: String): Action[AnyContent] = Action.async{
    val currentSettings = (query ? SettingsForMiner(address)).mapTo[MinerSettings]
    val owedBalance = db.run(Tables.Balances.filter(_.address === address).result)
    val pendingBalance = (query ? QueryMinerPending("any", address)).mapTo[Long] // ugly code, pool tag is ignored by this call to allow parallelization
    val changes = db.run(Tables.BalanceChanges.filter(_.address === address).sortBy(_.created.desc).take(10).map(_.amount).avg.result)
    val memberInfo = db.run(Tables.SubPoolMembers.filter(_.miner === address).sortBy(_.created.desc).take(1).result)
    val minerResponse = {
      for{
        settings <- currentSettings
        owed <- owedBalance
        pending <- pendingBalance
        avgDelta <- changes
        member <- memberInfo
      } yield {
        MinerResponse(settings.subpool, settings.paymentthreshold, Helpers.nanoErgToErg(pending), owed.map(_.amount).head, avgDelta.getOrElse(0.0), member.head)
      }
    }
    minerResponse.map(okJSON(_))
  }

  def getPayments(address: String): Action[AnyContent] = Action.async{
    val payments = db.run(Tables.Payments.filter(_.address === address).sortBy(_.created.desc).result)
    payments.map(okJSON(_))
  }

  def getRewards(address: String): Action[AnyContent] = Action.async{
    val rewards = db.run(Tables.BalanceChanges.filter(_.address === address).sortBy(_.created.desc).result)
    rewards.map(okJSON(_))
  }

  def getEarnings(address: String, i: String = DAILY): Action[AnyContent] = Action.async{
    val rewards = db.run(Tables.BalanceChanges.filter(_.address === address).sortBy(_.created.desc).result)
    rewards.map{
      rewardList =>
        i match {
          case DAILY =>
            val earningsList = rewardList.map(rl =>
              rl.copy(created = rl.created.truncatedTo(ChronoUnit.DAYS))).groupBy(_.created)
              .map(_._2.map(r => Earnings(r.address, r.coin, r.amount, r.created))
              )
            val earnings = earningsList.map(el => Earnings(el.head.address, el.head.coin, el.map(_.amount).sum, el.head.date)).toSeq
            okJSON(earnings)
          case MONTHLY =>
            val earningsList = rewardList.map(rl =>
              rl.copy(created = rl.created.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS))).groupBy(_.created)
              .map(_._2.map(r => Earnings(r.address, r.coin, r.amount, r.created))
              )
            val earnings = earningsList.map(el => Earnings(el.head.address, el.head.coin, el.map(_.amount).sum, el.head.date)).toSeq
            okJSON(earnings)
          case YEARLY =>
            val earningsList = rewardList.map(rl =>
              rl.copy(created = rl.created.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS))).groupBy(_.created)
              .map(_._2.map(r => Earnings(r.address, r.coin, r.amount, r.created))
              )
            val earnings = earningsList.map(el => Earnings(el.head.address, el.head.coin, el.map(_.amount).sum, el.head.date)).toSeq
            okJSON(earnings)
          case _ =>
            InternalServerError("An invalid interval was passed in!")
        }
    }
  }

}
