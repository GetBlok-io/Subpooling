
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
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.groups.builders.GenesisBuilder
import io.getblok.subpooling_core.groups.entities.{Pool, Subpool}
import io.getblok.subpooling_core.groups.selectors.EmptySelector
import io.getblok.subpooling_core.groups.{GenesisGroup, GroupManager}
import io.getblok.subpooling_core.persistence.models.DataTable
import io.getblok.subpooling_core.persistence.models.Models._
import io.getblok.subpooling_core.registers.PoolFees
import models.DatabaseModels.{BalanceChange, SMinerSettings}
import models.InvalidIntervalException
import models.ResponseModels.Intervals.{DAILY, MONTHLY, YEARLY}
import models.ResponseModels.{writesMinerResponse, _}
import org.ergoplatform.appkit._
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import slick.jdbc.{JdbcProfile, PostgresProfile}

import java.time.{LocalDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Named, Singleton}
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Api(value = "/miners", description = "Miner Operations")
@Singleton
class MinerController @Inject()(@Named("quick-db-reader") query: ActorRef,
                                components: ControllerComponents, system: ActorSystem, config: Configuration,
                                override protected val dbConfigProvider: DatabaseConfigProvider
                                ) extends SubpoolBaseController(components, config) with HasDatabaseConfigProvider[PostgresProfile]
                                {
  val log: Logger = Logger("MinerController")
  implicit val quickQueryContext: ExecutionContext = system.dispatchers.lookup("subpool-contexts.quick-query-dispatcher")
  implicit val timeOut: Timeout = Timeout(45 seconds)

  import dbConfig.profile.api._

  def getMiner(address: String): Action[AnyContent] = Action.async{
    val currentSettings = db.run(Tables.MinerSettingsTable.filter(_.address === address).result.headOption)
    val owedBalance = db.run(Tables.Balances.filter(_.address === address).result)
    val pendingBalance = db.run(Tables.PoolPlacementsTable.filter(_.miner === address).map(_.amount).sum.result)
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
        MinerResponse(settings.flatMap(s => s.subpool).getOrElse(paramsConfig.defaultPoolTag), settings.map(s => s.paymentthreshold).getOrElse(0.01), Helpers.nanoErgToErg(pending.getOrElse(0L)),
          owed.map(o => o.amount).headOption.getOrElse(0.0), avgDelta.getOrElse(0.0), member.headOption)
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
  def getPoolInfo(address: String): Action[AnyContent] = Action.async{
    val poolSettings = db.run(Tables.MinerSettingsTable.filter(_.address === address).result.headOption)
    val minerPool = poolSettings.map{
      s =>
        if(s.isDefined){
          db.run(Tables.PoolInfoTable.filter(_.poolTag === s.get.subpool.getOrElse(paramsConfig.defaultPoolTag)).result.head)
        }else{
          db.run(Tables.PoolInfoTable.filter(_.poolTag === paramsConfig.defaultPoolTag).result.head)
        }
    }.flatten
    minerPool.map(okJSON(_))
  }

  def getPoolStats(address: String): Action[AnyContent] = Action.async{

    val fMinerPool = db.run(Tables.MinerSettingsTable.filter(_.address === address).map(_.subpool).result.headOption)
    fMinerPool.map {
      minerPool =>
      val tag = minerPool.flatten.getOrElse(paramsConfig.defaultPoolTag)
      val fMiners = db.run(Tables.MinerSettingsTable.filter(_.subpool === tag).result)
      val fStats = db.run(Tables.MinerStats.filter(_.created < LocalDateTime.now().minusMinutes(10)).sortBy(_.created.desc).take(1).result.head)
      val fCurrStats = fStats.map(s => db.run(Tables.MinerStats.filter(ms => ms.created === s.created).result)).flatten

      for {
        currStats <- fCurrStats
        miners <- fMiners
      } yield {
        val filteredStats = currStats.filter(s => miners.exists(m => m.address == s.miner))
        val hashrate = filteredStats.map(_.hashrate).sum
        val shares = filteredStats.map(_.sharespersecond).sum
        okJSON(PoolStatistics(tag, hashrate, shares, None))
      }
    }.flatten
  }

  def getEarnings(address: String, i: String = DAILY): Action[AnyContent] = Action.async{
    val rewards = db.run(Tables.BalanceChanges.filter(_.address === address).sortBy(_.created.desc).result)
    rewards.map{
      rewardList =>
        i match {
          case DAILY =>
            val earningsList = rewardList.map(rl =>
              rl.copy(created = rl.created.withHour(0).truncatedTo(ChronoUnit.DAYS))).groupBy(_.created)
              .map(_._2.map(r => Earnings(r.address, r.coin, r.amount, r.created))
              )
            val earnings = earningsList.map(el => Earnings(el.head.address, el.head.coin, el.map(_.amount).sum, el.head.date)).toSeq
              .sortBy(_.date.toEpochSecond(ZoneOffset.UTC))
            okJSON(earnings)
          case MONTHLY =>
            val earningsList = rewardList.map(rl =>
              rl.copy(created = rl.created.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS))).groupBy(_.created)
              .map(_._2.map(r => Earnings(r.address, r.coin, r.amount, r.created))
              )
            val earnings = earningsList.map(el => Earnings(el.head.address, el.head.coin, el.map(_.amount).sum, el.head.date)).toSeq
              .sortBy(_.date.toEpochSecond(ZoneOffset.UTC))
            okJSON(earnings)
          case YEARLY =>
            val earningsList = rewardList.map(rl =>
              rl.copy(created = rl.created.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS))).groupBy(_.created)
              .map(_._2.map(r => Earnings(r.address, r.coin, r.amount, r.created))
              )
            val earnings = earningsList.map(el => Earnings(el.head.address, el.head.coin, el.map(_.amount).sum, el.head.date)).toSeq
              .sortBy(_.date.toEpochSecond(ZoneOffset.UTC))
            okJSON(earnings)
          case _ =>
            InternalServerError("An invalid interval was passed in!")
        }
    }
  }

  def setPaySettings(address: String): Action[AnyContent] = Action.async{
    implicit req =>
      val text = req.body.asText.get
      val split = text.split('|')
      val pay  = PayoutSettings(split(0), split(1).toDouble)
      val minerShares = db.run(Tables.PoolSharesTable.sortBy(_.created.desc).take(50000).filter(s => s.miner === address).result)

      minerShares.transformWith{
        case Success(ms) =>
          val sharesExist = minerShares.map(fS => fS.exists(s => s.ipaddress.split(':').contains(pay.ip)))

          val currSettings = db.run(Tables.MinerSettingsTable.filter(_.address === address).result.headOption)
          for{
            exist <- sharesExist
            settings <- currSettings
          } yield {
            if(exist){
              val payToUse = Math.max(pay.minPay, 0.01)
              if(settings.isDefined) {
                val q = for {s <- Tables.MinerSettingsTable if s.address === address} yield s.paymentThreshold

                db.run(q.update(payToUse))
                Ok
              }else{
                db.run(Tables.MinerSettingsTable += SMinerSettings(AppParameters.mcPoolId, address, payToUse, LocalDateTime.now(),
                  LocalDateTime.now(), Some(paramsConfig.defaultPoolTag)))
                Ok
              }
            }else{
              InternalServerError("Miner not found")
            }
          }
        case Failure(exception) =>
          log.error(s"There was an error while changing settings for ${address} with given ip ${pay.ip}", exception)
          Future(InternalServerError("Could not validate miner shares"))
      }

  }

  def setPoolSettings(address: String): Action[AnyContent] = Action.async{
    implicit req =>
      val text = req.body.asText.get
      val split = text.split('|')
      val sub  = SubPoolSettings(split(0), split(1))

      val minerShares = db.run(Tables.PoolSharesTable.sortBy(_.created.desc).take(50000).filter(s => s.miner === address).result)

      minerShares.transformWith{
        case Success(value) =>
          val isValid = db.run(Tables.PoolInfoTable.filter(i => i.poolTag === sub.subPool).result.headOption)
          val sharesExist = minerShares.map(ms => ms.exists(s => s.ipaddress.split(':').contains(sub.ip)))
          val currSettings = db.run(Tables.MinerSettingsTable.filter(ms => ms.address === address).result.headOption)
          for{
            v <- isValid
            settings <- currSettings
            exist <- sharesExist
          } yield {
            if(v.isDefined && exist){

              if(settings.isDefined) {
                val q = for {s <- Tables.MinerSettingsTable if s.address === address} yield s.subpool

                db.run(q.update(Some(sub.subPool)))
                Ok
              }else{
                db.run(Tables.MinerSettingsTable += SMinerSettings(AppParameters.mcPoolId, address, 0.01, LocalDateTime.now(),
                  LocalDateTime.now(), Some(sub.subPool)))
                Ok
              }
            }else{
              if(v.isEmpty)
                InternalServerError("Subpool not found")
              else
                InternalServerError("Miner shares not found, are you currently mining?")
            }
          }
        case Failure(exception) =>
          log.error(s"There was an error while getting shares to change miner settings for ${address}", exception)
          Future(InternalServerError("Could not validate miner shares"))
      }

  }

}
