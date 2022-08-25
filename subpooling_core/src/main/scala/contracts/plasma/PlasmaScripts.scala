package io.getblok.subpooling_core
package contracts.plasma

import global.AppParameters

import scala.io.Source

object PlasmaScripts {
  val plasmaBasePath = "conf/scripts/plasma/"
  val ext = ".ergo"

  sealed trait ScriptType
  case object SINGLE        extends ScriptType
  case object DUAL          extends ScriptType
  case object SINGLE_TOKEN  extends ScriptType

  def makeScript(name: String): String = {
    val src = Source.fromFile(AppParameters.scriptBasePath + plasmaBasePath + name + ext)
    val script = src.mkString
    src.close()
    script
  }

  // Script constants
  val BALANCE_STATE_SCRIPT: String = makeScript("BalanceState")

  // Emission Scripts
  val HYBRID_DEX_SCRIPT:    String = makeScript("HybridExchangedEmissions")

  // Command Scripts
  val SINGLE_INSERT_SCRIPT: String = makeScript("InsertBalance")
  val SINGLE_UPDATE_SCRIPT: String = makeScript("UpdateBalance")
  val SINGLE_PAYOUT_SCRIPT: String = makeScript("PayoutBalance")
  val SINGLE_DELETE_SCRIPT: String = makeScript("DeleteBalance")

  val TOKEN_PAYOUT_SCRIPT:  String = makeScript("TokenPayoutBalance")
  val TOKEN_UPDATE_SCRIPT:  String = makeScript("TokenUpdateBalance")

  val HYBRID_INSERT_SCRIPT: String = makeScript("DualInsertBalance")
  val HYBRID_UPDATE_SCRIPT: String = makeScript("DualUpdateBalance")
  val HYBRID_PAYOUT_SCRIPT: String = makeScript("DualPayoutBalance")
  val HYBRID_DELETE_SCRIPT: String = makeScript("DualDeleteBalance")

  // Holding Scripts
  val HOLDING_SCRIPT:       String = makeScript("PlasmaHolding")
}
