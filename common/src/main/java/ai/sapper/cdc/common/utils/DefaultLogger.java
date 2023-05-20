package ai.sapper.cdc.common.utils;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class DefaultLogger {
    private static Logger LOGGER = LoggerFactory.getLogger(DefaultLogger.class);

    public static void setDefaultLogger(@NonNull Logger logger) {
        LOGGER = logger;
    }

    public static boolean isGreaterOrEqual(Level l1, Level l2) {
        int i1 = l1.toInt();
        int i2 = l2.toInt();
        return (i1 >= i2);
    }

    public static String error(Throwable err, String format, Object... args) {
        String mesg = String.format(format, args);
        return String.format("%s : ERROR : %s\n", mesg, err.getLocalizedMessage());
    }

    public static String getStacktrace(@NonNull Throwable error) {
        StringBuilder buff = new StringBuilder(String.format("ERROR: %s", error.getLocalizedMessage()));
        buff.append("\n\t********************************BEGIN TRACE********************************\n");
        Throwable e = error;
        while (e != null) {
            buff.append("\n\t---------------------------------------------------------------------------\n");
            buff.append(String.format("\tERROR: %s\n", e.getLocalizedMessage()));
            for (StackTraceElement se : e.getStackTrace()) {
                buff.append(String.format("\t%s\n", se.toString()));
            }
            e = e.getCause();
        }
        buff.append("\t********************************END   TRACE********************************\n");

        return buff.toString();
    }

    public static void stacktrace( @NonNull Throwable error) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(getStacktrace(error));
        }
    }

    public static void stacktrace(Logger logger, @NonNull Throwable error) {
        if (logger == null) {
            logger = LOGGER;
        }
        if (logger.isDebugEnabled()) {
            logger.debug(getStacktrace(error));
        }
    }

    public static void error(@NonNull String msg) {
        LOGGER.error(msg);
    }

    public static void error(Logger logger, String msg) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.error(msg);
    }

    public static void error(String msg, @NonNull Throwable error) {
        LOGGER.error(msg, error);
    }

    public static void error(Logger logger, String msg, @NonNull Throwable error) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.error(msg, error);
    }

    public static void warn(String msg) {
        LOGGER.warn(msg);
    }

    public static void warn(Logger logger, String msg) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.warn(msg);
    }

    public static void warn(String msg, @NonNull Throwable error) {
        LOGGER.warn(msg, error);
    }

    public static void warn(Logger logger, String msg, @NonNull Throwable error) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.warn(msg, error);
    }

    public static void info(String msg) {
        LOGGER.info(msg);
    }

    public static void info(Logger logger, String msg) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.info(msg);
    }

    public static void debug(String msg, @NonNull Throwable error) {
        LOGGER.debug(msg, error);
    }

    public static void debug(Logger logger, String msg, @NonNull Throwable error) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.debug(msg, error);
    }

    public static void debug(String msg) {
        LOGGER.debug(msg);
    }

    public static void debug(Logger logger, String msg) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.debug(msg);
    }

    public static void trace(String msg) {
        LOGGER.trace(msg);
    }

    public static void trace(Logger logger, String msg) {
        if (logger == null) {
            logger = LOGGER;
        }
        logger.trace(msg);
    }
}
