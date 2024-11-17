package io.github.smyrgeorge.log4k.coroutines

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Level.DEBUG
import io.github.smyrgeorge.log4k.Level.ERROR
import io.github.smyrgeorge.log4k.Level.INFO
import io.github.smyrgeorge.log4k.Level.TRACE
import io.github.smyrgeorge.log4k.Level.WARN
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.coroutines.impl.SimpleCoroutinesLoggerFactory
import io.github.smyrgeorge.log4k.impl.MutableTags
import io.github.smyrgeorge.log4k.impl.Tag
import io.github.smyrgeorge.log4k.impl.Tags
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

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
     * @param message The log message to be recorded.
     * @param arguments Additional arguments to be included in the log event.
     * @param throwable An optional throwable associated with the log event.
     */
    private suspend fun log(
        level: Level,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?
    ) {
        if (!level.shouldLog()) return
        val ctx: LoggingContext = ctx()
        val event = toLoggingEvent(level, ctx, message, arguments, throwable)
        RootLogger.log(event)
    }

    /**
     * Converts the provided parameters into a `LoggingEvent` object.
     *
     * @param level The logging level of the event.
     * @param ctx The logging context associated with the event, or null if no context is available.
     * @param message The log message to be recorded.
     * @param arguments Additional arguments to be included in the log event.
     * @param throwable An optional throwable associated with the log event.
     * @return A `LoggingEvent` object encapsulating the provided details.
     */
    abstract fun toLoggingEvent(
        level: Level,
        ctx: LoggingContext,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?
    ): LoggingEvent

    fun Level.shouldLog(): Boolean = ordinal >= level.ordinal

    suspend inline fun trace(f: () -> String): Unit = if (TRACE.shouldLog()) trace(f()) else Unit
    suspend inline fun trace(t: Throwable, f: () -> String): Unit = if (TRACE.shouldLog()) trace(f(), t) else Unit
    suspend inline fun debug(f: () -> String): Unit = if (DEBUG.shouldLog()) debug(f()) else Unit
    suspend inline fun debug(t: Throwable, f: () -> String): Unit = if (DEBUG.shouldLog()) debug(f(), t) else Unit
    suspend inline fun info(f: () -> String): Unit = if (INFO.shouldLog()) info(f()) else Unit
    suspend inline fun info(t: Throwable, f: () -> String): Unit = if (INFO.shouldLog()) info(f(), t) else Unit
    suspend inline fun warn(f: () -> String): Unit = if (WARN.shouldLog()) warn(f()) else Unit
    suspend inline fun warn(t: Throwable, f: () -> String): Unit = if (WARN.shouldLog()) warn(f(), t) else Unit
    suspend inline fun error(f: () -> String?): Unit = if (ERROR.shouldLog()) error(f()) else Unit
    suspend inline fun error(t: Throwable, f: () -> String?): Unit = if (ERROR.shouldLog()) error(f(), t) else Unit

    suspend fun trace(msg: String, vararg args: Any?): Unit = log(TRACE, msg, args, null)
    suspend fun trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, msg, args, t)
    suspend fun debug(msg: String, vararg args: Any?): Unit = log(DEBUG, msg, args, null)
    suspend fun debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, msg, args, t)
    suspend fun info(msg: String, vararg args: Any?): Unit = log(INFO, msg, args, null)
    suspend fun info(msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, msg, args, t)
    suspend fun warn(msg: String, vararg args: Any?): Unit = log(WARN, msg, args, null)
    suspend fun warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, msg, args, t)
    suspend fun error(msg: String?, vararg args: Any?): Unit = log(ERROR, msg ?: "", args, null)
    suspend fun error(msg: String?, t: Throwable, vararg args: Any?): Unit = log(ERROR, msg ?: "", args, t)

    private suspend fun ctx(): LoggingContext = coroutineContext[LoggingContext] ?: LoggingContext.EMPTY

    companion object {
        val registry = CollectorRegistry<Logger>()
        var factory: CoroutinesLoggerFactory = SimpleCoroutinesLoggerFactory()
        fun of(name: String): Logger = factory.get(name)
        fun of(clazz: KClass<*>): Logger = factory.get(clazz)
        inline fun <reified T : Logger> ofType(name: String): T = of(name) as T
        inline fun <reified T : Logger> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}