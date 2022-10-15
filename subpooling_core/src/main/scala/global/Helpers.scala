package io.getblok.subpooling_core
package global

import io.getblok.subpooling_core.persistence.models.PersistenceModels.{PoolInformation, PoolMember, PoolState}
import io.getblok.subpooling_core.registers.PoolInfo
import org.ergoplatform.appkit.{ErgoId, ErgoToken, Parameters}

object Helpers {
  def ergToNanoErg(erg: Double): Long = (BigDecimal(erg) * Parameters.OneErg).longValue()

  def nanoErgToErg(nanoErg: Long): Double = (BigDecimal(nanoErg) / Parameters.OneErg).doubleValue()

  def toId(hex: String): ErgoId = ErgoId.create(hex)

  def trunc(str: String): String = {
    str.take(6) + "..." + str.takeRight(6)
  }


  def addTokens(token: ErgoToken, amnt: Long): ErgoToken = {
    new ErgoToken(token.getId, token.getValue + amnt)
  }


  def convertFromWhole(currency: String, wholeAmount: Long): Double = {
    currency match {
      case PoolInformation.CURR_ERG =>
        nanoErgToErg(wholeAmount)
      case PoolInformation.CURR_NETA =>
        (BigDecimal(wholeAmount) / 1000000).doubleValue()
      case PoolInformation.CURR_ERG_COMET =>
        nanoErgToErg(wholeAmount)
      case _ =>
        nanoErgToErg(wholeAmount)
    }
  }

  final val MinFee = Parameters.MinFee
  final val OneErg = Parameters.OneErg

}
