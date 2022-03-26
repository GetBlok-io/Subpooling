package logging

import java.util.Date
import java.util.logging.{Formatter, Level, LogRecord}

class ConsoleFormatter extends Formatter {
  private val pattern = "[%1$tT] [%2$s] [%3$s] %4$s %n"
  private val issuePattern = "[%1$tT] [%2$s] [%3$s] [\"%4$s\"] %5$s %n"
  override def format(record: LogRecord): String = {
    if(record.getLevel != Level.FINE) {
      String.format(pattern,
        new Date(record.getMillis),
        record.getLevel.getLocalizedName,
        record.getLoggerName,
        record.getMessage)
    }else{
      String.format(issuePattern,
        new Date(record.getMillis),
        record.getLevel.getLocalizedName,
        record.getLoggerName,
        s"${record.getSourceMethodName}@${record.getSourceClassName}",
        record.getMessage)
    }
  }
}
