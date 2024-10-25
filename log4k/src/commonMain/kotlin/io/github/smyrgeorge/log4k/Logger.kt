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

    private fun log(
        level: Level,
        span: Span?,
        msg: String,
        throwable: Throwable?,
        args: Array<out Any?>
    ) {
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

    fun trace(msg: String, vararg args: Any?): Unit = log(Level.TRACE, null, msg, null, args)
    fun trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.TRACE, null, msg, t, args)
    fun debug(msg: String, vararg args: Any?): Unit = log(Level.DEBUG, null, msg, null, args)
    fun debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.DEBUG, null, msg, t, args)
    fun info(msg: String, vararg args: Any?): Unit = log(Level.INFO, null, msg, null, args)
    fun info(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.INFO, null, msg, t, args)
    fun warn(msg: String, vararg args: Any?): Unit = log(Level.WARN, null, msg, null, args)
    fun warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.WARN, null, msg, t, args)
    fun error(msg: String?, vararg args: Any?): Unit = log(Level.ERROR, null, msg ?: "", null, args)
    fun error(msg: String?, t: Throwable, vararg args: Any?): Unit = log(Level.ERROR, null, msg ?: "", t, args)

    fun trace(span: Span, msg: String, vararg args: Any?): Unit = log(Level.TRACE, span, msg, null, args)
    fun trace(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.TRACE, span, msg, t, args)
    fun debug(span: Span, msg: String, vararg args: Any?): Unit = log(Level.DEBUG, span, msg, null, args)
    fun debug(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.DEBUG, span, msg, t, args)
    fun info(span: Span, msg: String, vararg args: Any?): Unit = log(Level.INFO, span, msg, null, args)
    fun info(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.INFO, span, msg, t, args)
    fun warn(span: Span, msg: String, vararg args: Any?): Unit = log(Level.WARN, span, msg, null, args)
    fun warn(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(Level.WARN, span, msg, t, args)
    fun error(span: Span, msg: String?, vararg args: Any?): Unit = log(Level.ERROR, span, msg ?: "", null, args)
    fun error(span: Span, msg: String?, t: Throwable, vararg args: Any?): Unit =
        log(Level.ERROR, span, msg ?: "", t, args)

    companion object {
        fun of(name: String): Logger = RootLogger.Logging.factory.get(name)
        fun of(clazz: KClass<*>): Logger = RootLogger.Logging.factory.get(clazz)
        inline fun <reified T : Logger> ofType(name: String): T = of(name) as T
        inline fun <reified T : Logger> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}