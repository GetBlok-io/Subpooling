
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

import java.time.LocalDateTime
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
        MinerResponse(settings.flatMap(_.subpool).getOrElse(paramsConfig.defaultPoolTag), settings.map(_.paymentthreshold).getOrElse(0.01), Helpers.nanoErgToErg(pending), owed.map(_.amount).head, avgDelta.getOrElse(0.0), member.head)
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

    val poolSettings = db.run(Tables.MinerSettingsTable.filter(_.address === address).result.headOption)
    val response = poolSettings.map{
      settings =>
        val tag: String = settings.flatMap(_.subpool).getOrElse(paramsConfig.defaultPoolTag)

        val fSettings = (query ? MinersByAssignedPool(tag)).mapTo[Seq[MinerSettings]]
        val fInfo = (query ? QueryPoolInfo(tag)).mapTo[PoolInformation]
        val fEffortDiff = fInfo.map{
          info =>
            val fShares = db.run(Tables.PoolSharesTable.getEffortDiff(tag, paramsConfig.defaultPoolTag, info.last_block))
            val fMiners = db.run(Tables.PoolSharesTable.queryPoolMiners(tag, paramsConfig.defaultPoolTag))

            for{
              shares <- fShares
              miners <- fMiners
            } yield {
              Some(shares.filter(s => miners.exists(s.miner == _.address)).map(s => s.difficulty / BigDecimal(s.networkdifficulty)).sum.toDouble)
            }
        }.flatten
        val fStats = db.run(Tables.MinerStats.sortBy(_.created.desc)
          .filter(_.created > LocalDateTime.now().minusHours(1))
          .result)

        fStats.transformWith{
          case Success(stats) =>
            val fPoolStats = for{
              settings <- fSettings
              effortDiff <- fEffortDiff
            } yield {
              val filteredStats = stats.filter(s => settings.exists(st => st.address == s.miner))
              if(filteredStats.nonEmpty) {
                val avgHash = filteredStats.groupBy(s => s.miner)
                  .map(s => s._1 -> s._2.map(ms => BigDecimal(ms.hashrate)).sum / filteredStats.size).values.sum
                val avgShares = filteredStats.groupBy(s => s.miner)
                  .map(s => s._1 -> s._2.map(ms => BigDecimal(ms.sharespersecond)).sum / filteredStats.size).values.sum
                val effort = (effortDiff.getOrElse(0.0) * AppParameters.shareConst)
                PoolStatistics(tag, avgHash.toDouble, avgShares.toDouble, effort.toDouble)
              }else{
                val effort = (effortDiff.getOrElse(0.0) * AppParameters.shareConst)
                PoolStatistics(tag, 0.0, 0.0, effort.toDouble)
              }
            }
            fPoolStats.map(okJSON(_))
          case Failure(ex: InvalidIntervalException) =>
            Future(InternalServerError(ex.getMessage))
          case Failure(ex: Exception) =>
            Future(InternalServerError("There was an unknown error while serving your request:\n"+ ex.getMessage))
      }

    }.flatten
    response

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

  def setPaySettings(address: String): Action[AnyContent] = Action.async{
    implicit req =>
      val json = req.body.asJson.get
      val pay  = json.as[PayoutSettings]
      val minerShares = db.run(Tables.PoolSharesTable.take(50000).filter(s => s.miner === address).result)
      val sharesExist = minerShares.map(_.exists(_.ipaddress.split(':').contains(pay.ip)))
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
  }

  def setPoolSettings(address: String): Action[AnyContent] = Action.async{
    implicit req =>
      val json = req.body.asJson.get
      val sub  = json.as[SubPoolSettings]
      val isValid = db.run(Tables.PoolInfoTable.filter(_.poolTag === sub.subPool).result.headOption)
      val minerShares = db.run(Tables.PoolSharesTable.take(50000).filter(s => s.miner === address).result)
      val sharesExist = minerShares.map(_.exists(_.ipaddress.split(':').contains(sub.ip)))
      val currSettings = db.run(Tables.MinerSettingsTable.filter(_.address === address).result.headOption)
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
          InternalServerError("Subpool not found or ip was incorrect")
        }
      }
  }

}
