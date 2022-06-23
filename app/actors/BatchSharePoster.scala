package actors

import actors.BatchSharePoster.{ShareBatch, emailShareError}
import actors.PushMessageNotifier.{BlockMessage, MessageRequest}
import akka.actor.Actor
import configs.ParamsConfig
import io.getblok.subpooling_core.global.{AppParameters, Helpers}
import io.getblok.subpooling_core.persistence.models.Models.Share
import models.DatabaseModels.PoolShare
import persistence.Tables
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}
import play.api.libs.mailer.{Email, MailerClient}
import slick.jdbc.PostgresProfile

import java.time.LocalDateTime
import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * Post shares from the master table to pool tables
 */
class BatchSharePoster @Inject()(config: Configuration, mailerClient: MailerClient, protected val dbConfigProvider: DatabaseConfigProvider)
  extends Actor with HasDatabaseConfigProvider[PostgresProfile]{

  private val logger: Logger     = Logger("BatchSharePoster")
  import dbConfig.profile.api._


  val params = new ParamsConfig(config)
  logger.info("Initiating BatchSharePoster")

  override def receive: Receive = {
    case batch: ShareBatch =>
      Try {
        logger.info(s"Posting batch of ${batch.shares.length} shares to pool ${batch.poolTag}")
        val poolShares = batch.shares.map {
          s => PoolShare(
            s.poolid,
            s.blockheight,
            s.miner,
            s.worker,
            s.difficulty,
            s.networkdifficulty,
            s.useragent,
            s.ipaddress,
            s.source,
            s.created,
            batch.poolTag
          )
        }
      db.run(Tables.PoolSharesTable ++= poolShares)
    }.recoverWith{
        case ex: Exception =>
          logger.error("There was a critical error while inserting shares into the database!", ex)
          mailerClient.send(emailShareError(ex, params.senderEmails, batch.poolTag, batch.shares.head, batch.shares.last))
          Failure(ex)
      }
  }
}

object BatchSharePoster {
  case class ShareBatch(shares: Seq[Share], poolTag: String)

  def emailShareError(error: Exception, receivers: Seq[String], poolTag: String, shareStart: Share, shareEnd: Share): Email = {
    Email(
      "CRITICAL Error While Posting Pool Shares!",
      "subpooling@getblok.io",
      receivers,
      Some(s"There was a critical error while posting shares to pool ${poolTag}." +
        s" \n The share batch started at ${shareStart.created.toString} and ended at ${shareEnd.created.toString}" +
        s" \n " +
        s" \n \n " + (error.getMessage) + "\n" + error.getStackTrace.mkString("\n", "\n ", "\n"))
    )
  }
}




