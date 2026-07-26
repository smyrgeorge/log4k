package io.github.smyrgeorge.log4k.slf4j

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import org.slf4j.Marker
import org.slf4j.helpers.LegacyAbstractLogger

/**
 * A logger implementation that adapts the Log4k logging facade to a legacy abstract logger.
 *
 * Every `org.slf4j.Logger` method is inherited from [LegacyAbstractLogger]/`AbstractLogger` and funnels into
 * [handleNormalizedLoggingCall], which is the only place that forwards to Log4k. Deliberately *not* overriding
 * the per-level methods is what gives us SLF4J's argument normalization for free — most importantly, extracting
 * a trailing [Throwable] argument (`log.error("failed for {}", id, e)`) so it is attached to the event instead of
 * being consumed as a formatting argument. The base class also short-circuits on `isXxxEnabled()` before
 * normalizing, so disabled levels cost nothing.
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
