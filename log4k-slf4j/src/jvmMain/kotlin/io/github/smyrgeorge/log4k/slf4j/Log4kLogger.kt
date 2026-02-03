package io.github.smyrgeorge.log4k.slf4j

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import org.slf4j.Marker
import org.slf4j.helpers.LegacyAbstractLogger

/**
 * A logger implementation that adapts the Log4k logging facade to a legacy abstract logger.
 *
 * @property log4k The underlying Log4k `Logger` instance used for logging.
 */
public class Log4kLogger(
    private val log4k: Logger
) : LegacyAbstractLogger() {
    override fun getName(): String = log4k.name

    override fun isTraceEnabled(): Boolean = log4k.isEnabled(Level.TRACE)
    override fun isDebugEnabled(): Boolean = log4k.isEnabled(Level.DEBUG)
    override fun isInfoEnabled(): Boolean = log4k.isEnabled(Level.INFO)
    override fun isWarnEnabled(): Boolean = log4k.isEnabled(Level.WARN)
    override fun isErrorEnabled(): Boolean = log4k.isEnabled(Level.ERROR)

    override fun error(msg: String?, t: Throwable?): Unit =
        log4k.log(Level.ERROR, null, msg ?: "null", emptyArray(), t)

    override fun error(format: String?, vararg arguments: Any?): Unit =
        log4k.log(Level.ERROR, null, format ?: "null", arguments, null)

    override fun error(format: String?, arg1: Any?, arg2: Any?): Unit =
        log4k.log(Level.ERROR, null, format ?: "null", arrayOf(arg1, arg2), null)

    override fun error(format: String?, arg: Any?): Unit =
        log4k.log(Level.ERROR, null, format ?: "null", arrayOf(arg), null)

    override fun error(msg: String?): Unit =
        log4k.log(Level.ERROR, null, msg ?: "null", emptyArray(), null)

    override fun warn(msg: String?, t: Throwable?): Unit =
        log4k.log(Level.WARN, null, msg ?: "null", emptyArray(), t)

    override fun warn(format: String?, vararg arguments: Any?): Unit =
        log4k.log(Level.WARN, null, format ?: "null", arguments, null)

    override fun warn(format: String?, arg1: Any?, arg2: Any?): Unit =
        log4k.log(Level.WARN, null, format ?: "null", arrayOf(arg1, arg2), null)

    override fun warn(format: String?, arg: Any?): Unit =
        log4k.log(Level.WARN, null, format ?: "null", arrayOf(arg), null)

    override fun warn(msg: String?): Unit =
        log4k.log(Level.WARN, null, msg ?: "null", emptyArray(), null)

    override fun info(msg: String?, t: Throwable?): Unit =
        log4k.log(Level.INFO, null, msg ?: "null", emptyArray(), t)

    override fun info(format: String?, vararg arguments: Any?): Unit =
        log4k.log(Level.INFO, null, format ?: "null", arguments, null)

    override fun info(format: String?, arg1: Any?, arg2: Any?): Unit =
        log4k.log(Level.INFO, null, format ?: "null", arrayOf(arg1, arg2), null)

    override fun info(format: String?, arg: Any?): Unit =
        log4k.log(Level.INFO, null, format ?: "null", arrayOf(arg), null)

    override fun info(msg: String?): Unit =
        log4k.log(Level.INFO, null, msg ?: "null", emptyArray(), null)

    override fun debug(msg: String?, t: Throwable?): Unit =
        log4k.log(Level.DEBUG, null, msg ?: "null", emptyArray(), t)

    override fun debug(format: String?, vararg arguments: Any?): Unit =
        log4k.log(Level.DEBUG, null, format ?: "null", arguments, null)

    override fun debug(format: String?, arg1: Any?, arg2: Any?): Unit =
        log4k.log(Level.DEBUG, null, format ?: "null", arrayOf(arg1, arg2), null)

    override fun debug(format: String?, arg: Any?): Unit =
        log4k.log(Level.DEBUG, null, format ?: "null", arrayOf(arg), null)

    override fun debug(msg: String?): Unit =
        log4k.log(Level.DEBUG, null, msg ?: "null", emptyArray(), null)

    override fun trace(msg: String?, t: Throwable?): Unit =
        log4k.log(Level.TRACE, null, msg ?: "null", emptyArray(), t)

    override fun trace(format: String?, vararg arguments: Any?): Unit =
        log4k.log(Level.TRACE, null, format ?: "null", arguments, null)

    override fun trace(format: String?, arg1: Any?, arg2: Any?): Unit =
        log4k.log(Level.TRACE, null, format ?: "null", arrayOf(arg1, arg2), null)

    override fun trace(format: String?, arg: Any?): Unit =
        log4k.log(Level.TRACE, null, format ?: "null", arrayOf(arg), null)

    override fun trace(msg: String?): Unit =
        log4k.log(Level.TRACE, null, msg ?: "null", emptyArray(), null)

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: org.slf4j.event.Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        fun org.slf4j.event.Level.toLevel(): Level =
            when (this) {
                org.slf4j.event.Level.TRACE -> Level.TRACE
                org.slf4j.event.Level.DEBUG -> Level.DEBUG
                org.slf4j.event.Level.INFO -> Level.INFO
                org.slf4j.event.Level.WARN -> Level.WARN
                org.slf4j.event.Level.ERROR -> Level.ERROR
            }

        log4k.log(
            level = level.toLevel(),
            span = null,
            message = messagePattern ?: "null",
            arguments = arguments ?: emptyArray(),
            throwable = throwable
        )
    }
}
