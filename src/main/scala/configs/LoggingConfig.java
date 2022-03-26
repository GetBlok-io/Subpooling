package configs;

public class LoggingConfig {
    private String fileLoggingLevel;
    private String consoleLoggingLevel;
    private String logPath;
    private int maxNumLogs;
    private int maxLogSizeInBytes;


    public LoggingConfig(int numLogs, int numBytes){
        fileLoggingLevel = "INFO";
        consoleLoggingLevel = "INFO";
        maxNumLogs = numLogs;
        maxLogSizeInBytes = numBytes;
        logPath = "";
    }


    public String getFileLoggingLevel() {
        return fileLoggingLevel;
    }

    public void setFileLoggingLevel(String fileLoggingLevel) {
        this.fileLoggingLevel = fileLoggingLevel;
    }

    public String getConsoleLoggingLevel() {
        return consoleLoggingLevel;
    }

    public void setConsoleLoggingLevel(String consoleLoggingLevel) {
        this.consoleLoggingLevel = consoleLoggingLevel;
    }

    public int getMaxNumLogs() {
        return maxNumLogs;
    }

    public void setMaxNumLogs(int maxNumLogs) {
        this.maxNumLogs = maxNumLogs;
    }

    public int getMaxLogSizeInBytes() {
        return maxLogSizeInBytes;
    }

    public void setMaxLogSizeInBytes(int maxLogSizeInBytes) {
        this.maxLogSizeInBytes = maxLogSizeInBytes;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
}
