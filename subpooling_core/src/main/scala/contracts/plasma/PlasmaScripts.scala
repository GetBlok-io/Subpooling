package io.getblok.subpooling_core
package contracts.plasma

import global.AppParameters

import scala.io.Source

object PlasmaScripts {
  val plasmaBasePath = "conf/scripts/plasma/"
  val ext = ".ergo"

  sealed trait ScriptType
  case object SINGLE extends ScriptType
  case object DUAL   extends ScriptType

  def makeScript(name: String): String = {
    val src = Source.fromFile(AppParameters.scriptBasePath + plasmaBasePath + name + ext)
    val script = src.mkString
    src.close()
    script
  }

  // Script constants
  val BALANCE_STATE_SCRIPT: String = makeScript("BalanceState")

  val HYBRID_DEX_SCRIPT:    String = makeScript("HybridExchangedEmissions")

  val SINGLE_INSERT_SCRIPT: String = makeScript("InsertBalance")
  val SINGLE_UPDATE_SCRIPT: String = makeScript("UpdateBalance")
  val SINGLE_PAYOUT_SCRIPT: String = makeScript("PayoutBalance")
  val SINGLE_DELETE_SCRIPT: String = makeScript("DeleteBalance")

  val HYBRID_INSERT_SCRIPT: String = makeScript("InsertBalance")
  val HYBRID_UPDATE_SCRIPT: String = makeScript("UpdateBalance")
  val HYBRID_PAYOUT_SCRIPT: String = makeScript("PayoutBalance")
  val HYBRID_DELETE_SCRIPT: String = makeScript("DeleteBalance")
}
