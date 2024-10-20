package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.reflect.KClass

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class Logger(
    override val name: String,
    private var level: Level
) : LoggerRegistry.Collector {
    private var levelBeforeMute: Level = level

    private fun log(level: Level, msg: String, args: Array<out Any?>) =
        log(level, msg, null, args)

    private fun log(level: Level, msg: String, throwable: Throwable?, args: Array<out Any?>) {
        if (!level.shouldLog()) return
        val event = toLoggingEvent(level, msg, throwable, args)
        RootLogger.log(event)
    }

    abstract fun toLoggingEvent(level: Level, msg: String, throwable: Throwable?, args: Array<out Any?>): LoggingEvent

    private fun Level.shouldLog(): Boolean =
        ordinal >= level.ordinal

    override fun getLevel(): Level = level

    override fun setLevel(level: Level) {
        this.level = level
    }

    override fun mute() {
        levelBeforeMute = level
        level = Level.OFF
    }

    override fun unmute() {
        level = levelBeforeMute
        levelBeforeMute = level
    }

    fun trace(f: () -> String): Unit = if (Level.TRACE.shouldLog()) trace(f()) else Unit
    fun trace(msg: String, vararg args: Any?): Unit = log(Level.TRACE, msg, args)
    fun debug(f: () -> String): Unit = if (Level.DEBUG.shouldLog()) debug(f()) else Unit
    fun debug(msg: String, vararg args: Any?): Unit = log(Level.DEBUG, msg, args)
    fun info(f: () -> String): Unit = if (Level.INFO.shouldLog()) info(f()) else Unit
    fun info(msg: String, vararg args: Any?): Unit = log(Level.INFO, msg, args)
    fun warn(f: () -> String): Unit = if (Level.WARN.shouldLog()) warn(f()) else Unit
    fun warn(msg: String, vararg args: Any?): Unit = log(Level.WARN, msg, args)
    fun error(f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(f()) else Unit
    fun error(msg: String?, vararg args: Any?): Unit = log(Level.ERROR, msg ?: "", args)
    fun error(t: Throwable, f: () -> String?): Unit = if (Level.ERROR.shouldLog()) error(f(), t) else Unit
    fun error(msg: String?, t: Throwable, vararg args: Any?): Unit = log(Level.ERROR, msg ?: "", t, args)

    companion object {
        fun of(name: String): Logger = RootLogger.Logging.factory.get(name)
        fun of(clazz: KClass<*>): Logger = RootLogger.Logging.factory.get(clazz)
    }
}