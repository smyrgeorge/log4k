package io.github.smyrgeorge.log4k.examples

import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.annotation.NoTrace
import io.github.smyrgeorge.log4k.annotation.Tag
import io.github.smyrgeorge.log4k.annotation.Traced
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleTracingAppender
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object TracedCompilerPlugin {

    @NoTrace
    class DisabledService {
        @Traced(name = "should-not-appear")
        context(_: TracingContext)
        fun ignored(): String = "ignored"
    }

    @Traced(tags = [Tag("layer", "service")])
    class UserService {
        context(_: TracingContext)
        fun load(id: Long): String = "user-$id" // traced -> span "UserService.load" (parent = ctx's current)

        @NoTrace
        context(_: TracingContext)
        fun secret(): String = "shh" // opted out

        // No TracingContext/Span in scope -> a ROOT span "UserService.helper" is created via the
        // synthesized `private val _trace_ = Tracer.of(this::class)`.
        fun helper(): Int = 42
    }

    // --- span in scope (no TracingContext) ---

    @Traced(name = "child-op")
    context(_: TracingEvent.Span.Local)
    fun childOp(): String = "child-done" // parent = the Span.Local in scope

    // --- suspend functions ---

    @Traced(name = "outer-span", tags = [Tag("component", "billing"), Tag("tier", "gold")])
    context(_: TracingContext)
    suspend fun outer(): Int {
        val n = inner()
        return n + 1
    }

    @Traced // no name -> defaults to "ClassName.functionName": "TracedCompilerPlugin.inner"
    context(_: TracingContext)
    suspend fun inner(): Int {
        delay(10.milliseconds)
        return 41
    }

    // --- non-suspend functions ---

    @Traced(name = "compute")
    context(_: TracingContext)
    fun compute(x: Int): Int {
        return square(x) + 1
    }

    @Traced // no name -> defaults to "ClassName.functionName": "TracedCompilerPlugin.square"
    context(_: TracingContext)
    fun square(x: Int): Int {
        return x * x
    }

    // --- exception path ---

    @Traced(name = "boom")
    context(_: TracingContext)
    suspend fun boom() {
        delay(5.milliseconds)
        error("kaboom")
    }

    fun run() = runBlocking {
        // Start from a clean slate so each line is printed exactly once (RootLogger registers a
        // default logging appender, and earlier examples may have registered their own).
        RootLogger.Logging.appenders.unregisterAll()
        RootLogger.Tracing.appenders.unregisterAll()
        RootLogger.Logging.appenders.register(SimpleConsoleLoggingAppender())
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

        // class-level @Traced: `load` (span 'UserService.load') and `helper` (root span
        // 'UserService.helper', via the synthesized `_trace_`) are both traced; `secret` opts out.
        val service = UserService()
        with(TracingContext.create(tracer = tracer)) {
            println(">> load()   -> ${service.load(7)}   (traced -> span 'UserService.load')")
            println(">> secret() -> ${service.secret()}       (@NoTrace -> NOT traced)")
            println(">> helper() -> ${service.helper()}          (root span 'UserService.helper')")
        }

        delay(1.seconds)

        // A span in scope (the receiver of `tracer.span { }`) is used as the parent — no TracingContext.
        tracer.span("parent-op") {
            println(">> childOp() -> ${childOp()}   (child span of 'parent-op')")
        }

        delay(1.seconds)

        // class-level @NoTrace kill switch: no spans at all, even though `ignored` has @Traced.
        with(TracingContext.create(tracer = tracer)) {
            println(">> ignored() -> ${DisabledService().ignored()}  (@NoTrace class -> NOT traced)")
        }

        // Give the async tracing appender time to flush.
        delay(1.seconds)
    }
}
