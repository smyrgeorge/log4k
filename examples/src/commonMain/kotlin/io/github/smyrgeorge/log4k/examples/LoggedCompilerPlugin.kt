package io.github.smyrgeorge.log4k.examples

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.annotation.Logged
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleTracingAppender
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object LoggedCompilerPlugin {

    // Class-level @Logged: every eligible public member is instrumented with entry/exit logging.
    // `Calculator` declares no `log`, so the plugin synthesizes `private val _log_ = Logger.of(this::class)`.
    @Logged
    class Calculator {
        fun add(a: Int, b: Int): Int = a + b // INFO (class-level default)

        @Logged(level = Level.DEBUG) // per-function override -> logged at DEBUG
        fun mul(a: Int, b: Int): Int = a * b
    }

    class UserRepository {
        // Declared by the user and reused by the plugin (no field is generated).
        @Suppress("unused") // used by the generated @Logged code (added after the frontend).
        private val log = Logger.of(this::class)

        // A TracingContext context parameter -> the current span is attached to every log line.
        @Logged
        context(_: TracingContext)
        suspend fun load(id: Long): String {
            delay(10.milliseconds)
            return "user-$id"
        }

        // Exception path: the failure is logged at ERROR (with the throwable) and rethrown.
        @Logged(level = Level.WARN)
        fun boom(): Nothing = error("kaboom")
    }

    // Stands in for a foreign logger type (e.g. org.slf4j.Logger) that happens to be named `log`.
    class ForeignLogger

    class InventoryService {
        // A `log` member of a foreign type: the plugin ignores it (it is not a log4k Logger) and
        // synthesizes its own `private val _log_ = Logger.of(this::class)` — no name clash, no error.
        @Suppress("unused")
        private val log = ForeignLogger()

        @Logged
        fun restock(sku: String): String = "restocked-$sku"
    }

    fun run() = runBlocking {
        RootLogger.Logging.level = Level.TRACE // so the DEBUG line from `mul` is shown too.

        // Start from a clean slate so each line is printed exactly once (RootLogger registers a
        // default logging appender, and earlier examples may have registered their own).
        RootLogger.Logging.appenders.unregisterAll()
        RootLogger.Tracing.appenders.unregisterAll()
        RootLogger.Logging.appenders.register(SimpleConsoleLoggingAppender())
        RootLogger.Tracing.appenders.register(SimpleConsoleTracingAppender())

        val tracer = Tracer.of("demo")

        // class-level @Logged + synthesized logger.
        val calc = Calculator()
        println(">> add(2, 3) -> ${calc.add(2, 3)}")   // INFO  entry/exit -> "Calculator.add"
        println(">> mul(4, 5) -> ${calc.mul(4, 5)}")   // DEBUG entry/exit -> "Calculator.mul"

        delay(500.milliseconds)

        // TracingContext context parameter -> the log lines carry the active span id.
        val repo = UserRepository()
        val ctx = TracingContext.create(tracer = tracer)
        val span = tracer.span("request").start()
        ctx.current = span
        with(ctx) {
            println(">> load(7)   -> ${repo.load(7)}") // logs carry span '${span.context.spanId}'
        }
        span.end()

        delay(500.milliseconds)

        // exception path: logged at ERROR and rethrown.
        try {
            repo.boom()
        } catch (e: IllegalStateException) {
            println(">> caught rethrown exception: ${e.message}")
        }

        delay(500.milliseconds)

        // Foreign `log` member: the plugin synthesizes a separate `_log_` logger instead of erroring.
        val inventory = InventoryService()
        println(">> restock -> ${inventory.restock("abc")}")

        // Give the async logging/tracing appenders time to flush.
        delay(1.seconds)
    }
}
