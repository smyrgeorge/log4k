package io.github.smyrgeorge.log4k.slf4j

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import org.slf4j.Marker
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.spi.LoggingEventAware
import org.slf4j.event.LoggingEvent as Slf4jLoggingEvent

/**
 * A logger implementation that adapts the Log4k logging facade to a legacy abstract logger.
 *
 * Every classic `org.slf4j.Logger` method is inherited from [LegacyAbstractLogger]/`AbstractLogger` and
 * funnels into [handleNormalizedLoggingCall]. Deliberately *not* overriding the per-level methods is what
 * gives us SLF4J's argument normalization for free — most importantly, extracting a trailing [Throwable]
 * argument (`log.error("failed for {}", id, e)`) so it is attached to the event instead of being consumed
 * as a formatting argument. The base class also short-circuits on `isXxxEnabled()` before normalizing, so
 * disabled levels cost nothing.
 *
 * Fluent-API calls (`logger.atInfo().addKeyValue("tenant", "acme").log("…")`) arrive through
 * [LoggingEventAware.log] instead: SLF4J routes the fully built event here because this logger is
 * [LoggingEventAware], so the key-value pairs survive as the log4k event's **tags** — rather than being
 * flattened into the message text by SLF4J's non-aware fallback.
 *
 * @property log4k The underlying Log4k `Logger` instance used for logging.
 */
public class Log4kLogger(
    private val log4k: Logger
) : LegacyAbstractLogger(), LoggingEventAware {
    override fun getName(): String = log4k.name

    override fun isTraceEnabled(): Boolean = log4k.isEnabled(Level.TRACE)
    override fun isDebugEnabled(): Boolean = log4k.isEnabled(Level.DEBUG)
    override fun isInfoEnabled(): Boolean = log4k.isEnabled(Level.INFO)
    override fun isWarnEnabled(): Boolean = log4k.isEnabled(Level.WARN)
    override fun isErrorEnabled(): Boolean = log4k.isEnabled(Level.ERROR)

    override fun getFullyQualifiedCallerName(): String? = null

    /** The classic (non-fluent) SLF4J path — no key-value pairs exist here. */
    override fun handleNormalizedLoggingCall(
        level: org.slf4j.event.Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        log4k.log(
            level = level.toLevel(),
            span = null,
            tags = emptyMap(),
            message = messagePattern ?: "null",
            arguments = arguments ?: emptyArray(),
            throwable = throwable,
        )
    }

    /**
     * The fluent-API path: forwards the built event's message, arguments and throwable, and turns its
     * key-value pairs into the log4k event's tags. A `null` key-value value is rendered as `"null"`,
     * since log4k tag values are non-null.
     */
    override fun log(event: Slf4jLoggingEvent) {
        log4k.log(
            level = event.level.toLevel(),
            span = null,
            tags = event.keyValuePairs?.associate { it.key to (it.value ?: "null") } ?: emptyMap(),
            message = event.message ?: "null",
            arguments = event.argumentArray ?: emptyArray(),
            throwable = event.throwable,
        )
    }

    private fun org.slf4j.event.Level.toLevel(): Level =
        when (this) {
            org.slf4j.event.Level.TRACE -> Level.TRACE
            org.slf4j.event.Level.DEBUG -> Level.DEBUG
            org.slf4j.event.Level.INFO -> Level.INFO
            org.slf4j.event.Level.WARN -> Level.WARN
            org.slf4j.event.Level.ERROR -> Level.ERROR
        }
}
