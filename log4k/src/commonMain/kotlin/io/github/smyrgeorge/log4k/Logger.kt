@file:Suppress("unused")

package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlin.reflect.KClass

/**
 * An abstract logger class providing logging functionality across different levels.
 * Extends the `CollectorRegistry.Collector` class.
 *
 * @property name The name identifier for the logger.
 * @property level The logging level threshold.
 */
abstract class Logger(
    final override val name: String,
    final override var level: Level
) : CollectorRegistry.Collector {
    override var levelBeforeMute: Level = level

    /**
     * Logs a message with the specified logging level and additional context.
     *
     * @param level The logging level of the event.
     * @param span An optional span that can be used for tracing the context.
     * @param message The log message to be recorded.
     * @param arguments Additional arguments to be included in the log event.
     * @param throwable An optional throwable associated with the log event.
     */
    fun log(
        level: Level,
        span: Span?,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?
    ) {
        if (!level.shouldLog()) return
        val event = toLoggingEvent(level, span, message, arguments, throwable)
        RootLogger.log(event)
    }

    /**
     * Converts the provided logging information into a `LoggingEvent`.
     *
     * @param level The logging level of the event.
     * @param span An optional span that can be used for tracing the context.
     * @param message The log message to be recorded.
     * @param arguments Additional arguments to be included in the log event.
     * @param throwable An optional throwable associated with the log event.
     * @return A `LoggingEvent` representing the logging details.
     */
    abstract fun toLoggingEvent(
        level: Level,
        span: Span?,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?
    ): LoggingEvent

    /**
     * Checks if logging is enabled for the specified logging level.
     *
     * @param level The logging level to be checked.
     * @return `true` if logging is enabled for the specified level, `false` otherwise.
     */
    fun isEnabled(level: Level): Boolean = level.shouldLog()
    fun Level.shouldLog(): Boolean = ordinal >= level.ordinal

    companion object {
        val registry = CollectorRegistry<Logger>()
        var factory: LoggerFactory = SimpleLoggerFactory()
        fun of(name: String): Logger = factory.get(name)
        fun of(clazz: KClass<*>): Logger = factory.get(clazz)
    }
}
