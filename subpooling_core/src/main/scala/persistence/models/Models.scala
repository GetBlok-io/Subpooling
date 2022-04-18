package io.getblok.subpooling_core
package persistence.models

import io.getblok.subpooling_core.groups.entities.Member
import io.getblok.subpooling_core.registers.MemberInfo
import org.ergoplatform.appkit.{Address, Parameters}

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.time.LocalDateTime

object Models {
  abstract class DatabaseConversion[T] {
    protected def fromResultSet(resultSet: ResultSet): T

    protected def str(idx: Int)(implicit rs: ResultSet): String = {
      rs.getString(idx)
    }

    protected def long(idx: Int)(implicit rs: ResultSet): Long = {
      rs.getLong(idx)
    }

    protected def date(idx: Int)(implicit rs: ResultSet): LocalDateTime = {
      rs.getObject(idx, classOf[LocalDateTime])
    }

    protected def dec(idx: Int)(implicit rs: ResultSet): Double = {
      rs.getDouble(idx)
    }

    protected def int(idx: Int)(implicit rs: ResultSet): Int = {
      rs.getInt(idx)
    }
    protected def bool(idx: Int)(implicit rs: ResultSet): Boolean = {
      rs.getBoolean(idx)
    }

  }

  case class DbConn(c: Connection) {
    def state(s: String): PreparedStatement = c.prepareStatement(s)

    def close(): Unit = c.close()
  }

  case class PoolMember(subpool: String, subpool_id: Long, tx: String, box: String, g_epoch: Long, epoch: Long,
                        height: Long, miner: String, share_score: Long, share: Long, share_perc: Double,
                        minpay: Long, stored: Long, paid: Long, change: Long, epochs_mined: Long,
                        token: String, token_paid: Long, block: Long, created: LocalDateTime)

  object PoolMember extends DatabaseConversion[PoolMember] {
    override def fromResultSet(rs: ResultSet): PoolMember = {
      implicit val resultSet: ResultSet = rs
      PoolMember(str(1), long(2), str(3), str(4), long(5), long(6), long(7),
        str(8), long(9), long(10), dec(11), long(12), long(13), long(14), long(15),
        long(16), str(17), long(18), long(19), date(20))
    }
  }

  case class PoolState(subpool: String, subpool_id: Long, title: String, box: String, tx: String, g_epoch: Long, epoch: Long,
                       g_height: Long, height: Long, status: String, members: Int, block: Long, creator: String,
                       stored_id: String, stored_val: Long, updated: LocalDateTime, created: LocalDateTime)

  object PoolState extends DatabaseConversion[PoolState] {
    override def fromResultSet(rs: ResultSet): PoolState = {
      implicit val resultSet: ResultSet = rs
      PoolState(str(1), long(2), str(3), str(4), str(5), long(6), long(7),
        long(8), long(9), str(10), int(11), long(12), str(13), str(14),
        long(15), date(16), date(17))
    }

    val SUCCESS   = "success"
    val FAILURE   = "failure"
    val INITIATED = "initiated"
    val CONFIRMED = "confirmed"

  }

  case class PoolInformation(poolTag: String, g_epoch: Long, subpools: Long, last_block: Long,
                             total_members: Long, value_locked: Long, total_paid: Long, currency: String, payment_type: String,
                             fees: Long, official: Boolean, epoch_kick: Long, max_members: Long, title: String, creator: String,
                             updated: LocalDateTime, created: LocalDateTime)

  object PoolInformation extends DatabaseConversion[PoolInformation] {
    override def fromResultSet(rs: ResultSet): PoolInformation = {
      implicit val resultSet: ResultSet = rs
      PoolInformation(str(1), long(2), long(3), long(4), long(5), long(6), long(7),
         str(8), str(9), long(10), bool(11), long(12), long(13), str(14), str(15),
        date(16), date(17))
    }

    val CURR_ERG = "ERG"
    val CURR_NETA = "NETA"
    val CURR_COMET = "COMET"
    val CURR_NUGS  = "Nuggies"
    val CURR_TEST_TOKENS = "tToken"
    val TEST_ID = "2adb1b96acbcc10a8c6138a7f968c657f1130f70559d8f49abb391ac72800f0f"

    val PAY_PPLNS = "PPLNS"
    val PAY_PPS   = "PPS"
    val PAY_EQ    = "EQUAL"
  }



  case class PoolPlacement(subpool: String, subpool_id: Long, block: Long, holding_id: String, holding_val: Long,
                           miner: String, score: Long, minpay: Long, epochs_mined: Long, amount: Long,
                           epoch: Long, g_epoch: Long){
    /**
     * Converts placement into partially loaded member
     * @return Member with score, minpay, and epochs mined, but empty stored value and miner tag
     */
    def toPartialMember: Member = {
      Member(Address.create(miner),  new MemberInfo(Array(score, minpay, 0L, epochs_mined, 0L)))
    }
  }

  object PoolPlacement extends DatabaseConversion[PoolPlacement] {
    override def fromResultSet(rs: ResultSet): PoolPlacement = {
      implicit val resultSet: ResultSet = rs
      PoolPlacement(str(1), long(2), long(3), str(4), long(5), str(6), long(7),
        long(8), long(9), long(10), long(11), long(12))
    }
  }

  case class Block(id: Long, blockheight: Long, netDiff: Double, status: String, confirmationprogress: Double, miner: String, reward: Double,
                   hash: String, created: LocalDateTime){
    def getErgReward: Long = (BigDecimal(reward) * Parameters.OneErg).longValue()
  }

  object Block extends DatabaseConversion[Block] {
    override def fromResultSet(rs: ResultSet): Block = {
      implicit val resultSet: ResultSet = rs
      Block(long(1), long(3), dec(4), str(5), dec(7), str(10), dec(11),
        str(13), date(14))
    }

    val PENDING     = "pending"
    val INITIATED   = "initiated"
    val CONFIRMED   = "confirmed"
    val PROCESSING  = "processing"
    val PAID        = "paid"
    val ORPHANED    = "orphaned"
  }

  case class Share(blockheight: Long, miner: String, worker: String, difficulty: Double, networkdifficulty: Double,
                   created: LocalDateTime)

  object Share extends DatabaseConversion[Share] {
    override def fromResultSet(rs: ResultSet): Share = {
      implicit val resultSet: ResultSet = rs
      Share(long(2), str(5), str(6), dec(3), dec(4), date(10))
    }
  }

  case class MinerSettings(address: String, paymentthreshold: Double, created: LocalDateTime, updated: LocalDateTime,
                           subpool: String)
  object MinerSettings extends DatabaseConversion[MinerSettings] {
    override def fromResultSet(rs: ResultSet): MinerSettings = {
      implicit val resultSet: ResultSet = rs
      MinerSettings(str(2), dec(3), date(4), date(5), str(7))
    }
  }

}