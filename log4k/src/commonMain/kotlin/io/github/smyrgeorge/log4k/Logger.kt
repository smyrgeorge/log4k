@file:Suppress("unused")

package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlin.reflect.KClass
import kotlin.time.TimeSource

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
        if (!level.enabled()) return
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
    fun isEnabled(level: Level): Boolean = level.enabled()
    fun Level.enabled(): Boolean = ordinal >= level.ordinal

    /**
     * Executes [f] while emitting entry/exit log lines around it, and an error line if it throws.
     *
     * This is the runtime helper the `log4k-compiler-plugin` generates a call to when a function is
     * annotated with [io.github.smyrgeorge.log4k.annotation.Logged]. It is `inline`, so it works for
     * both regular and `suspend` functions, and it emits every line through [log] directly.
     *
     * Emitted lines:
     * - `"→ name(args)"` on entry, at [level].
     * - `"← name = result (duration)"` on normal completion, at [level].
     * - `"✗ name failed (duration)"` at [Level.ERROR] — with the throwable attached — if [f] throws;
     *   the throwable is then rethrown.
     *
     * @param T The type of the result produced by [f].
     * @param level The level used for the entry/exit lines.
     * @param span The span (if any) to attach to every emitted line, correlating the logs with a trace.
     * @param name The (already formatted) name of the instrumented function.
     * @param args The (already formatted) argument list rendered inside the entry line's parentheses.
     * @param f The block to execute.
     * @return The result produced by [f].
     */
    inline fun <T> logged(
        level: Level,
        span: Span?,
        name: String,
        args: String,
        f: () -> T
    ): T {
        log(level, span, "→ $name($args)", emptyArray<Any?>(), null)
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val result = f()
            log(level, span, "← $name = $result (${mark.elapsedNow()})", emptyArray<Any?>(), null)
            result
        } catch (e: Throwable) {
            log(Level.ERROR, span, "✗ $name failed (${mark.elapsedNow()})", emptyArray<Any?>(), e)
            throw e
        }
    }

    companion object {
        val registry = CollectorRegistry<Logger>()
        var factory: LoggerFactory = SimpleLoggerFactory()
        fun of(name: String): Logger = factory.get(name)
        fun of(clazz: KClass<*>): Logger = factory.get(clazz)
    }
}
