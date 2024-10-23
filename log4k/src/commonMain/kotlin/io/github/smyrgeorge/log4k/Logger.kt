package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.reflect.KClass

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class Logger(
    final override val name: String,
    final override var level: Level
) : LoggerRegistry.Collector {
    private var levelBeforeMute: Level = level

    private fun log(level: Level, span: Span?, msg: String, args: Array<out Any?>) = log(level, span, msg, null, args)
    private fun log(level: Level, span: Span?, msg: String, throwable: Throwable?, args: Array<out Any?>) {
        if (!level.shouldLog()) return
        val event = toLoggingEvent(level, span, msg, throwable, args)
        RootLogger.log(event)
    }

    abstract fun toLoggingEvent(
        level: Level,
        span: Span?,
        msg: String,
        throwable: Throwable?,
        args: Array<out Any?>
    ): LoggingEvent

    fun Level.shouldLog(): Boolean =
        ordinal >= level.ordinal

    override fun mute() {
        levelBeforeMute = level
        level = Level.OFF
    }

    override fun unmute() {
        level = levelBeforeMute
        levelBeforeMute = level
    }

    inline fun trace(f: () -> String): Unit = if (Level.TRACE.shouldLog()) trace(f()) else Unit
    inline fun debug(f: () -> String): Unit = if (Level.DEBUG.shouldLog()) debug(f()) else Unit
    inline fun info(f: () -> String): Unit = if (Level.INFO.shouldLog()) info(f()) else Unit
    inline fun warn(f: () -> String): Unit = if (Level.WARN.shouldLog()) warn(f()) else Unit
    inline fun error(f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(f()) else Unit
    inline fun error(t: Throwable, f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(f(), t) else Unit

    inline fun trace(span: Span, f: () -> String): Unit = if (Level.TRACE.shouldLog()) trace(span, f()) else Unit
    inline fun debug(span: Span, f: () -> String): Unit = if (Level.DEBUG.shouldLog()) debug(span, f()) else Unit
    inline fun info(span: Span, f: () -> String): Unit = if (Level.INFO.shouldLog()) info(span, f()) else Unit
    inline fun warn(span: Span, f: () -> String): Unit = if (Level.WARN.shouldLog()) warn(span, f()) else Unit
    inline fun error(span: Span, f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(span, f()) else Unit
    inline fun error(span: Span, t: Throwable, f: () -> String?): Unit =
        if (Level.ERROR.shouldLog()) error(span, f(), t) else Unit

    fun trace(msg: String, vararg args: Any?): Unit = log(Level.TRACE, null, msg, args)
    fun debug(msg: String, vararg args: Any?): Unit = log(Level.DEBUG, null, msg, args)
    fun info(msg: String, vararg args: Any?): Unit = log(Level.INFO, null, msg, args)
    fun warn(msg: String, vararg args: Any?): Unit = log(Level.WARN, null, msg, args)
    fun error(msg: String?, vararg args: Any?): Unit = log(Level.ERROR, null, msg ?: "", args)
    fun error(msg: String?, t: Throwable, vararg args: Any?): Unit = log(Level.ERROR, null, msg ?: "", t, args)

    fun trace(span: Span, msg: String, vararg args: Any?): Unit = log(Level.TRACE, span, msg, args)
    fun debug(span: Span, msg: String, vararg args: Any?): Unit = log(Level.DEBUG, span, msg, args)
    fun info(span: Span, msg: String, vararg args: Any?): Unit = log(Level.INFO, span, msg, args)
    fun warn(span: Span, msg: String, vararg args: Any?): Unit = log(Level.WARN, span, msg, args)
    fun error(span: Span, msg: String?, vararg args: Any?): Unit = log(Level.ERROR, span, msg ?: "", args)
    fun error(span: Span, msg: String?, t: Throwable, vararg args: Any?): Unit =
        log(Level.ERROR, span, msg ?: "", t, args)

    companion object {
        fun of(name: String): Logger = RootLogger.Logging.factory.get(name)
        fun of(clazz: KClass<*>): Logger = RootLogger.Logging.factory.get(clazz)
    }
}