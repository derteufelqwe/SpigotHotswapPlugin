package de.derteufelqwe.SpigotHotswap;

/**
 * Custom logger, since Javas default logger doesn't work correctly.
 */
public class Logger {

    private LogLevel logLevel = LogLevel.INFO;


    public Logger() {

    }


    private String formatMessage(String msg) {
        return "[SHP] " + msg;
    }


    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }


    public LogLevel getLevel() {
        return this.logLevel;
    }


    /**
     * Checks if a message should be logged
     *
     * @param logLevel Level of the message to be sent
     */
    private boolean isLoggable(LogLevel logLevel) {
        if (this.logLevel.getValue() > logLevel.getValue()) {
            return false;
        }

        return true;
    }


    public void all(String msg) {
        if (isLoggable(LogLevel.ALL)) {
            System.out.println(this.formatMessage(msg));
        }
    }

    public void debug(String msg) {
        if (isLoggable(LogLevel.DEBUG)) {
            System.out.println(this.formatMessage(msg));
        }
    }

    public void info(String msg) {
        if (isLoggable(LogLevel.INFO)) {
            System.out.println(this.formatMessage(msg));
        }
    }

    public void error(String msg) {
        if (isLoggable(LogLevel.ERROR)) {
            System.err.println(this.formatMessage(msg));
        }
    }


    enum LogLevel {
        ALL(0),
        DEBUG(1),
        INFO(2),
        ERROR(3);

        private int value;

        private LogLevel(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

    }

}
