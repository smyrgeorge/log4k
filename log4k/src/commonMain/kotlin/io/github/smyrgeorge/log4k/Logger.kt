package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.Level.DEBUG
import io.github.smyrgeorge.log4k.Level.ERROR
import io.github.smyrgeorge.log4k.Level.INFO
import io.github.smyrgeorge.log4k.Level.TRACE
import io.github.smyrgeorge.log4k.Level.WARN
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
@Suppress("unused", "MemberVisibilityCanBePrivate")
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

    //@formatter:off
    context(ctx: TracingContext) inline fun trace(f: () -> String): Unit = if (TRACE.shouldLog()) trace(f()) else Unit
    context(ctx: TracingContext) inline fun trace(t: Throwable, f: () -> String): Unit = if (TRACE.shouldLog()) trace(f(), t) else Unit
    context(ctx: TracingContext) inline fun debug(f: () -> String): Unit = if (DEBUG.shouldLog()) debug(f()) else Unit
    context(ctx: TracingContext) inline fun debug(t: Throwable, f: () -> String): Unit = if (DEBUG.shouldLog()) debug(f(), t) else Unit
    context(ctx: TracingContext) inline fun info(f: () -> String): Unit = if (INFO.shouldLog()) info(f()) else Unit
    context(ctx: TracingContext) inline fun info(t: Throwable, f: () -> String): Unit = if (INFO.shouldLog()) info(f(), t) else Unit
    context(ctx: TracingContext) inline fun warn(f: () -> String): Unit = if (WARN.shouldLog()) warn(f()) else Unit
    context(ctx: TracingContext) inline fun warn(t: Throwable, f: () -> String): Unit = if (WARN.shouldLog()) warn(f(), t) else Unit
    context(ctx: TracingContext) inline fun error(f: () -> String?): Unit = if (ERROR.shouldLog()) error(f()) else Unit
    context(ctx: TracingContext) inline fun error(t: Throwable, f: () -> String?): Unit = if (ERROR.shouldLog()) error(f(), t) else Unit

    context(ctx: TracingContext) fun trace(msg: String, vararg args: Any?): Unit = log(TRACE, ctx.spans.current(), msg, args, null)
    context(ctx: TracingContext) fun trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, ctx.spans.current(), msg, args, t)
    context(ctx: TracingContext) fun debug(msg: String, vararg args: Any?): Unit = log(DEBUG, ctx.spans.current(), msg, args, null)
    context(ctx: TracingContext) fun debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, ctx.spans.current(), msg, args, t)
    context(ctx: TracingContext) fun info(msg: String, vararg args: Any?): Unit = log(INFO, ctx.spans.current(), msg, args, null)
    context(ctx: TracingContext) fun info(msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, ctx.spans.current(), msg, args, t)
    context(ctx: TracingContext) fun warn(msg: String, vararg args: Any?): Unit = log(WARN, ctx.spans.current(), msg, args, null)
    context(ctx: TracingContext) fun warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, ctx.spans.current(), msg, args, t)
    context(ctx: TracingContext) fun error(msg: String?, vararg args: Any?): Unit = log(ERROR, ctx.spans.current(), msg ?: "", args, null)
    context(ctx: TracingContext) fun error(msg: String?, t: Throwable, vararg args: Any?): Unit = log(ERROR, ctx.spans.current(), msg ?: "", args, t)

    inline fun trace(f: () -> String): Unit = if (TRACE.shouldLog()) trace(f()) else Unit
    inline fun trace(t: Throwable, f: () -> String): Unit = if (TRACE.shouldLog()) trace(f(), t) else Unit
    inline fun debug(f: () -> String): Unit = if (DEBUG.shouldLog()) debug(f()) else Unit
    inline fun debug(t: Throwable, f: () -> String): Unit = if (DEBUG.shouldLog()) debug(f(), t) else Unit
    inline fun info(f: () -> String): Unit = if (INFO.shouldLog()) info(f()) else Unit
    inline fun info(t: Throwable, f: () -> String): Unit = if (INFO.shouldLog()) info(f(), t) else Unit
    inline fun warn(f: () -> String): Unit = if (WARN.shouldLog()) warn(f()) else Unit
    inline fun warn(t: Throwable, f: () -> String): Unit = if (WARN.shouldLog()) warn(f(), t) else Unit
    inline fun error(f: () -> String?): Unit = if (ERROR.shouldLog()) error(f()) else Unit
    inline fun error(t: Throwable, f: () -> String?): Unit = if (ERROR.shouldLog()) error(f(), t) else Unit

    inline fun trace(span: Span, f: () -> String): Unit = if (TRACE.shouldLog()) trace(span, f()) else Unit
    inline fun trace(span: Span, t: Throwable, f: () -> String): Unit = if (TRACE.shouldLog()) trace(span, f(), t) else Unit
    inline fun debug(span: Span, f: () -> String): Unit = if (DEBUG.shouldLog()) debug(span, f()) else Unit
    inline fun debug(span: Span, t: Throwable, f: () -> String): Unit = if (DEBUG.shouldLog()) debug(span, f(), t) else Unit
    inline fun info(span: Span, f: () -> String): Unit = if (INFO.shouldLog()) info(span, f()) else Unit
    inline fun info(span: Span, t: Throwable, f: () -> String): Unit = if (INFO.shouldLog()) info(span, f(), t) else Unit
    inline fun warn(span: Span, f: () -> String): Unit = if (WARN.shouldLog()) warn(span, f()) else Unit
    inline fun warn(span: Span, t: Throwable, f: () -> String): Unit = if (WARN.shouldLog()) warn(span, f(), t) else Unit
    inline fun error(span: Span, f: () -> String?): Unit = if (ERROR.shouldLog()) error(span, f()) else Unit
    inline fun error(span: Span, t: Throwable, f: () -> String?): Unit = if (ERROR.shouldLog()) error(span, f(), t) else Unit

    fun trace(msg: String, vararg args: Any?): Unit = log(TRACE, null, msg, args, null)
    fun trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, null, msg, args, t)
    fun debug(msg: String, vararg args: Any?): Unit = log(DEBUG, null, msg, args, null)
    fun debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, null, msg, args, t)
    fun info(msg: String, vararg args: Any?): Unit = log(INFO, null, msg, args, null)
    fun info(msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, null, msg, args, t)
    fun warn(msg: String, vararg args: Any?): Unit = log(WARN, null, msg, args, null)
    fun warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, null, msg, args, t)
    fun error(msg: String?, vararg args: Any?): Unit = log(ERROR, null, msg ?: "", args, null)
    fun error(msg: String?, t: Throwable, vararg args: Any?): Unit = log(ERROR, null, msg ?: "", args, t)

    fun trace(span: Span, msg: String, vararg args: Any?): Unit = log(TRACE, span, msg, args, null)
    fun trace(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, span, msg, args, t)
    fun debug(span: Span, msg: String, vararg args: Any?): Unit = log(DEBUG, span, msg, args, null)
    fun debug(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, span, msg, args, t)
    fun info(span: Span, msg: String, vararg args: Any?): Unit = log(INFO, span, msg, args, null)
    fun info(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, span, msg, args, t)
    fun warn(span: Span, msg: String, vararg args: Any?): Unit = log(WARN, span, msg, args, null)
    fun warn(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, span, msg, args, t)
    fun error(span: Span, msg: String?, vararg args: Any?): Unit = log(ERROR, span, msg ?: "", args, null)
    fun error(span: Span, msg: String?, t: Throwable, vararg args: Any?): Unit = log(ERROR, span, msg ?: "", args, t)
    //@formatter:on

    companion object {
        val registry = CollectorRegistry<Logger>()
        var factory: LoggerFactory = SimpleLoggerFactory()
        fun of(name: String): Logger = factory.get(name)
        fun of(clazz: KClass<*>): Logger = factory.get(clazz)
        inline fun <reified T : Logger> ofType(name: String): T = of(name) as T
        inline fun <reified T : Logger> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}