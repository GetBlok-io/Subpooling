package io.getblok.subpooling_core
package logging

import org.slf4j.Logger

import java.util.logging.{ConsoleHandler, FileHandler}

object LoggingHandler {

  object loggers {
    val LOG_MAIN = "Main"
    val LOG_TEST = "Test"

    val LOG_PERSISTENCE = "Persistence"
    val LOG_PAYMENT_HANDLER = "PaymentHandler"
    val LOG_GEN_METADATA_CMD = "GenerateMetadataCmd"
    val LOG_MODIFY_SMARTPOOL_CMD = "ModifySmartPoolCmd"
    val LOG_DISTRIBUTE_REWARDS_CMD = "DistributeRewardsCmd"
    val LOG_SEND_TO_HOLDING_CMD = "SendToHoldingCmd"
    val LOG_RESET_STATUS_CMD = "ResetStatusCmd"
    val LOG_CLEAN_DB_CMD = "CheckAndCleanDbCmd"
    val LOG_PAY_BALANCES_CMD = "PayoutBalancesCmd"
    val LOG_INIT_VOTE_CMD = "InitializeVoteTokensCmd"
    val LOG_INIT_POV_CMD = "InitializePOVTokensCmd"
    val LOG_GEN_RECORDING_CMD = "GenerateRecordingCmd"
    val LOG_VOTE_COLLECTION_CMD = "VoteCollectionCmd"

    val LOG_NODE_HANDLER = "NodeHandler"

    val LOG_COMMAND_TX = "CommandTx"
    val LOG_DIST_TX = "DistributionTx"
    val LOG_HOLD_CONTRACT = "HoldingContract"
    val LOG_GEN_TX = "GenesisTx"
    val LOG_MOD_TX = "ModificationTx"
    val LOG_REGROUP_TX = "RegroupTx"
    val LOG_BOX_HELPER = "BoxHelper"

    val LOG_DIST_GRP = "DistributionGroup"
    val LOG_SUB_SEL = "SubpoolSelector"
    val LOG_HOLD_GRP = "HoldingGroup"
    val LOG_HOLD_CHAIN = "HoldingChain"
    val LOG_CMD_CHAIN = "CommandChain"
    val LOG_DIST_CHAIN = "DistributionChain"

    val loggerNames = List(
      LOG_MAIN, LOG_TEST,
      LOG_PERSISTENCE, LOG_PAYMENT_HANDLER, LOG_GEN_METADATA_CMD, LOG_MODIFY_SMARTPOOL_CMD, LOG_DISTRIBUTE_REWARDS_CMD,
      LOG_SEND_TO_HOLDING_CMD, LOG_RESET_STATUS_CMD, LOG_NODE_HANDLER, LOG_PAY_BALANCES_CMD, LOG_CLEAN_DB_CMD, LOG_INIT_VOTE_CMD,
      LOG_GEN_RECORDING_CMD, LOG_VOTE_COLLECTION_CMD,
      LOG_COMMAND_TX, LOG_DIST_TX, LOG_GEN_TX, LOG_MOD_TX, LOG_BOX_HELPER, LOG_REGROUP_TX, LOG_HOLD_CONTRACT,
      LOG_DIST_GRP, LOG_HOLD_GRP, LOG_SUB_SEL, LOG_HOLD_CHAIN, LOG_DIST_CHAIN, LOG_CMD_CHAIN

    )

  }


  import java.util.logging.LogManager

  LogManager.getLogManager.reset()
  val globalLogger: java.util.logging.Logger = java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME)
  globalLogger.setLevel(java.util.logging.Level.OFF)

  def initializeLogger(logger: org.slf4j.Logger, fileHandler: FileHandler, consoleHandler: ConsoleHandler): Logger = {
    val javaLogger: java.util.logging.Logger = java.util.logging.Logger.getLogger(logger.getName)
    val fileFormatter = new FileFormatter()
    val consoleFormatter = new ConsoleFormatter()

    fileHandler.setFormatter(fileFormatter)
    consoleHandler.setFormatter(consoleFormatter)

    javaLogger.addHandler(fileHandler)
    javaLogger.addHandler(consoleHandler)


    logger
  }



}




