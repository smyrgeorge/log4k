package io.github.smyrgeorge.log4k.examples

import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.annotation.NoTrace
import io.github.smyrgeorge.log4k.annotation.Tag
import io.github.smyrgeorge.log4k.annotation.Trace
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleTracingAppender
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object CompilerPlugin {

    @NoTrace
    class DisabledService {
        @Trace(name = "should-not-appear")
        context(_: TracingContext)
        fun ignored(): String = "ignored"
    }

    @Trace(tags = [Tag("layer", "service")])
    class UserService {
        context(_: TracingContext)
        fun load(id: Long): String = "user-$id" // traced -> span "UserService.load"

        @NoTrace
        context(_: TracingContext)
        fun secret(): String = "shh" // opted out

        fun helper(): Int = 42 // no TracingContext context parameter -> skipped
    }

    // --- suspend functions ---

    @Trace(name = "outer-span", tags = [Tag("component", "billing"), Tag("tier", "gold")])
    context(_: TracingContext)
    suspend fun outer(): Int {
        val n = inner()
        return n + 1
    }

    @Trace // no name -> defaults to "ClassName.functionName": "CompilerPlugin.inner"
    context(_: TracingContext)
    suspend fun inner(): Int {
        delay(10.milliseconds)
        return 41
    }

    // --- non-suspend functions ---

    @Trace(name = "compute")
    context(_: TracingContext)
    fun compute(x: Int): Int {
        return square(x) + 1
    }

    @Trace // no name -> defaults to "ClassName.functionName": "CompilerPlugin.square"
    context(_: TracingContext)
    fun square(x: Int): Int {
        return x * x
    }

    // --- exception path ---

    @Trace(name = "boom")
    context(_: TracingContext)
    suspend fun boom() {
        delay(5.milliseconds)
        error("kaboom")
    }

    fun run() = runBlocking {
        RootLogger.Tracing.appenders.register(SimpleConsoleTracingAppender())
        val tracer = Tracer.of("demo")

        // suspend: outer -> inner (nested spans)
        val suspended = with(TracingContext.create(tracer = tracer)) { outer() }
        println(">> outer (suspend) returned $suspended")

        delay(1.seconds)

        // non-suspend: compute -> square (nested spans)
        val plain = with(TracingContext.create(tracer = tracer)) { compute(5) }
        println(">> compute (non-suspend) returned $plain")

        delay(1.seconds)

        // exception: span is marked ERROR and the throwable is rethrown
        try {
            with(TracingContext.create(tracer = tracer)) { boom() }
        } catch (e: IllegalStateException) {
            println(">> caught rethrown exception: ${e.message}")
        }

        delay(1.seconds)

        // class-level @Trace: only `load` is traced. Expect exactly one span below:
        // 'UserService.load'. `secret` (@NoTrace) and `helper` (no context param) produce none.
        val service = UserService()
        with(TracingContext.create(tracer = tracer)) {
            println(">> load()   -> ${service.load(7)}   (traced -> span 'UserService.load')")
            println(">> secret() -> ${service.secret()}       (@NoTrace -> NOT traced)")
            println(">> helper() -> ${service.helper()}          (no TracingContext -> NOT traced)")
        }

        delay(1.seconds)

        // class-level @NoTrace kill switch: no spans at all, even though `ignored` has @Trace.
        with(TracingContext.create(tracer = tracer)) {
            println(">> ignored() -> ${DisabledService().ignored()}  (@NoTrace class -> NOT traced)")
        }

        // Give the async tracing appender time to flush.
        delay(1.seconds)
    }
}
