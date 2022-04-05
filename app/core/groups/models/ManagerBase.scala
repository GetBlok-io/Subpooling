package io.getblok.subpooling
package core.groups.models

import org.slf4j.{Logger, LoggerFactory}

import java.io.{PrintWriter, StringWriter}

abstract class ManagerBase {
  protected val managerName: String
  def logger: Logger = LoggerFactory.getLogger(managerName)

  def logStacktrace(e: Throwable): Unit = {
    val sw = new StringWriter()
    e.printStackTrace(new PrintWriter(sw))
    logger.error(sw.toString)
  }
  class StageManagerException extends Exception("Exception thrown during Stage Execution")
  class ChainManagerException extends Exception(s"Total failure for all pools during Chain Execution")
}
