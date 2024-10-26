package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.reflect.KClass

/**
 * Abstract base class for creating customizable loggers.
 *
 * @property name The name of the logger.
 * @property level The logging level of the logger.
 */
@Suppress("unused")
abstract class Logger(
    final override val name: String,
    final override var level: Level
) : LoggerRegistry.Collector {
    private var levelBeforeMute: Level = level

    private fun log(
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

    fun Level.shouldLog(): Boolean =
        ordinal >= level.ordinal

    /**
     * Mutes the logger by setting its logging level to `Level.OFF`.
     *
     * This method saves the current logging level in the `levelBeforeMute` field before muting.
     */
    override fun mute() {
        levelBeforeMute = level
        level = Level.OFF
    }

    /**
     * Reverts the logger to its previous logging level before it was muted.
     *
     * This method restores the logging level stored in `levelBeforeMute` back to `level`.
     * The `levelBeforeMute` field will also be updated to reflect the current `level`.
     */
    override fun unmute() {
        level = levelBeforeMute
        levelBeforeMute = level
    }

    inline fun trace(f: () -> String): Unit = if (Level.TRACE.shouldLog()) trace(f()) else Unit
    inline fun trace(t: Throwable, f: () -> String): Unit = if (Level.TRACE.shouldLog()) trace(f(), t) else Unit
    inline fun debug(f: () -> String): Unit = if (Level.DEBUG.shouldLog()) debug(f()) else Unit
    inline fun debug(t: Throwable, f: () -> String): Unit = if (Level.DEBUG.shouldLog()) debug(f(), t) else Unit
    inline fun info(f: () -> String): Unit = if (Level.INFO.shouldLog()) info(f()) else Unit
    inline fun info(t: Throwable, f: () -> String): Unit = if (Level.INFO.shouldLog()) info(f(), t) else Unit
    inline fun warn(f: () -> String): Unit = if (Level.WARN.shouldLog()) warn(f()) else Unit
    inline fun warn(t: Throwable, f: () -> String): Unit = if (Level.WARN.shouldLog()) warn(f(), t) else Unit
    inline fun error(f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(f()) else Unit
    inline fun error(t: Throwable, f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(f(), t) else Unit

    inline fun trace(span: Span, f: () -> String): Unit = if (Level.TRACE.shouldLog()) trace(span, f()) else Unit
    inline fun trace(span: Span, t: Throwable, f: () -> String): Unit =
        if (Level.TRACE.shouldLog()) trace(span, f(), t) else Unit

    inline fun debug(span: Span, f: () -> String): Unit = if (Level.DEBUG.shouldLog()) debug(span, f()) else Unit
    inline fun debug(span: Span, t: Throwable, f: () -> String): Unit =
        if (Level.DEBUG.shouldLog()) debug(span, f(), t) else Unit

    inline fun info(span: Span, f: () -> String): Unit = if (Level.INFO.shouldLog()) info(span, f()) else Unit
    inline fun info(span: Span, t: Throwable, f: () -> String): Unit =
        if (Level.INFO.shouldLog()) info(span, f(), t) else Unit

    inline fun warn(span: Span, f: () -> String): Unit = if (Level.WARN.shouldLog()) warn(span, f()) else Unit
    inline fun warn(span: Span, t: Throwable, f: () -> String): Unit =
        if (Level.WARN.shouldLog()) warn(span, f(), t) else Unit

    inline fun error(span: Span, f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(span, f()) else Unit
    inline fun error(span: Span, t: Throwable, f: () -> String?): Unit =
        if (Level.ERROR.shouldLog()) error(span, f(), t) else Unit

    fun trace(msg: String, vararg args: Any?): Unit = log(Level.TRACE, null, msg, args, null)
    fun trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.TRACE, null, msg, args, t)
    fun debug(msg: String, vararg args: Any?): Unit = log(Level.DEBUG, null, msg, args, null)
    fun debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.DEBUG, null, msg, args, t)
    fun info(msg: String, vararg args: Any?): Unit = log(Level.INFO, null, msg, args, null)
    fun info(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.INFO, null, msg, args, t)
    fun warn(msg: String, vararg args: Any?): Unit = log(Level.WARN, null, msg, args, null)
    fun warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.WARN, null, msg, args, t)
    fun error(msg: String?, vararg args: Any?): Unit = log(Level.ERROR, null, msg ?: "", args, null)
    fun error(msg: String?, t: Throwable, vararg args: Any?): Unit = log(Level.ERROR, null, msg ?: "", args, t)

    fun trace(span: Span, msg: String, vararg args: Any?): Unit = log(Level.TRACE, span, msg, args, null)
    fun trace(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.TRACE, span, msg, args, t)
    fun debug(span: Span, msg: String, vararg args: Any?): Unit = log(Level.DEBUG, span, msg, args, null)
    fun debug(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.DEBUG, span, msg, args, t)
    fun info(span: Span, msg: String, vararg args: Any?): Unit = log(Level.INFO, span, msg, args, null)
    fun info(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.INFO, span, msg, args, t)
    fun warn(span: Span, msg: String, vararg args: Any?): Unit = log(Level.WARN, span, msg, args, null)
    fun warn(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.WARN, span, msg, args, t)
    fun error(span: Span, msg: String?, vararg args: Any?): Unit = log(Level.ERROR, span, msg ?: "", args, null)
    fun error(span: Span, msg: String?, t: Throwable, vararg args: Any?): Unit =
        log(Level.ERROR, span, msg ?: "", args, t)

    companion object {
        fun of(name: String): Logger = RootLogger.Logging.factory.get(name)
        fun of(clazz: KClass<*>): Logger = RootLogger.Logging.factory.get(clazz)
        inline fun <reified T : Logger> ofType(name: String): T = of(name) as T
        inline fun <reified T : Logger> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}