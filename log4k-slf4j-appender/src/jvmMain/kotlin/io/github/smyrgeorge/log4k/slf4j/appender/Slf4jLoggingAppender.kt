package io.github.smyrgeorge.log4k.slf4j.appender

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender
import org.slf4j.ILoggerFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.event.Level as Slf4jLevel

/**
 * An [Appender] that forwards every log4k [LoggingEvent] to SLF4J — the reverse direction of the
 * `log4k-slf4j` module.
 *
 * Use it to adopt log4k inside an existing JVM project that already has an SLF4J backend (Logback,
 * Log4j2, …) configured: everything emitted through the log4k API — including the entry/exit lines the
 * `log4k-compiler-plugin` generates for `@Logged` functions — lands in that backend instead of log4k's
 * default console appender. [install] performs the swap:
 *
 * ```kotlin
 * Slf4jLoggingAppender.install()
 * ```
 *
 * Forwarding uses the SLF4J 2.x fluent API and hands over the **raw** message pattern together with its
 * arguments: log4k's `{}` placeholder rules deliberately mirror SLF4J's `MessageFormatter`, so
 * substitution can be left to the backend, and structured encoders (JSON, logstash, …) keep the
 * individual arguments instead of receiving a pre-flattened string. A [Throwable] on the event becomes
 * the SLF4J cause, the event's tags are attached as key-value pairs, and when the event is
 * span-correlated the span's `traceId`/`spanId` are attached as key-value pairs as well. Events at
 * [Level.OFF] are dropped, and levels disabled in the backend cost only a no-op builder.
 *
 * Two gates apply in this setup: log4k's own logger levels decide whether an event is published at all,
 * and the backend's configuration decides whether the forwarded call is written. Keep the log4k side at
 * least as verbose as the backend, so the backend configuration remains the single source of truth.
 *
 * Because delivery goes through `RootLogger`'s asynchronous queue, the backend observes the forwarding
 * coroutine, not the original call site: caller-location patterns (`%class`, `%line`), `%thread`, and the
 * backend timestamp refer to the moment of forwarding. The original values remain available on the
 * [LoggingEvent] (`thread`, `timestamp`) for custom bridges that need full fidelity. When the event
 * carries a compile-time [io.github.smyrgeorge.log4k.SourceLocation] (injected by the `log4k-compiler-plugin`),
 * the **real** source location survives the bridge as key-value pairs named after
 * logstash-logback-encoder's caller-data fields — `caller_file_name` / `caller_line_number` /
 * `caller_method_name` — so a JSON backend renders it exactly like Logback's own caller data, even
 * though the backend's `%class`/`%line` cannot see past the forwarding coroutine.
 *
 * This appender must never run while SLF4J itself is backed by log4k (the `log4k-slf4j` provider):
 * every forwarded event would be routed straight back into log4k, endlessly. Construction fails fast
 * with a descriptive error if that binding is detected.
 */
public class Slf4jLoggingAppender : Appender<LoggingEvent> {

    init {
        checkNotBackedByLog4k(LoggerFactory.getILoggerFactory())
    }

    override val name: String = requireNotNull(this::class.qualifiedName)

    private val loggers = ConcurrentHashMap<String, org.slf4j.Logger>()

    override suspend fun append(event: LoggingEvent) {
        val level = event.level.toSlf4j() ?: return
        // A backend-disabled level yields SLF4J's NOP builder, so the calls below are free no-ops.
        var builder = loggers.computeIfAbsent(event.logger, LoggerFactory::getLogger).atLevel(level)
        event.arguments.forEach { builder = builder.addArgument(it) }
        event.throwable?.let { builder = builder.setCause(it) }
        event.tags.forEach { (key, value) -> builder = builder.addKeyValue(key, value) }
        event.span?.context?.let {
            builder = builder.addKeyValue("traceId", it.traceId)
            builder = builder.addKeyValue("spanId", it.spanId)
        }
        event.callSite?.let {
            // logstash-logback-encoder's caller-data field names, so a JSON backend renders the
            // forwarded call site exactly like Logback's own caller data.
            builder = builder.addKeyValue("caller_file_name", it.file)
            builder = builder.addKeyValue("caller_line_number", it.line)
            it.function.substringAfterLast('.').takeIf(String::isNotBlank)?.let { method ->
                builder = builder.addKeyValue("caller_method_name", method)
            }
        }
        builder.log(event.message)
    }

    private fun Level.toSlf4j(): Slf4jLevel? = when (this) {
        Level.TRACE -> Slf4jLevel.TRACE
        Level.DEBUG -> Slf4jLevel.DEBUG
        Level.INFO -> Slf4jLevel.INFO
        Level.WARN -> Slf4jLevel.WARN
        Level.ERROR -> Slf4jLevel.ERROR
        Level.OFF -> null
    }

    public companion object {
        /**
         * Makes SLF4J the sink for log4k logging: unregisters the default [SimpleConsoleLoggingAppender]
         * (so nothing is logged twice) and registers a [Slf4jLoggingAppender] in its place. Appenders
         * registered deliberately by the application are left untouched.
         *
         * @return The registered appender, e.g., for later unregistration.
         */
        public fun install(): Slf4jLoggingAppender {
            val appender = Slf4jLoggingAppender()
            RootLogger.Logging.appenders.unregister(SimpleConsoleLoggingAppender::class)
            RootLogger.Logging.appenders.register(appender)
            return appender
        }

        /**
         * Fails fast when [factory] is the `log4k-slf4j` provider's logger factory, in which case forwarding
         * log4k events to SLF4J would route them straight back into log4k and loop forever. Compared by
         * fully qualified class name, so this module needs no dependency on `log4k-slf4j`.
         */
        internal fun checkNotBackedByLog4k(factory: ILoggerFactory) {
            check(factory::class.qualifiedName != "io.github.smyrgeorge.log4k.slf4j.Log4kILoggerFactory") {
                "SLF4J is backed by log4k itself (the log4k-slf4j provider is bound), so forwarding log4k " +
                        "events to SLF4J would loop them straight back into log4k. Remove either the log4k-slf4j " +
                        "dependency or this appender: the two bridge opposite directions and cannot coexist."
            }
        }
    }
}
