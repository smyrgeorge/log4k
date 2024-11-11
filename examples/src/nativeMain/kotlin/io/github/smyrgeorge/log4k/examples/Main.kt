package io.github.smyrgeorge.log4k.examples

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.impl.SimpleLogger
import io.github.smyrgeorge.log4k.impl.appenders.BatchAppender
import io.github.smyrgeorge.log4k.impl.appenders.FlowFloodProtectedAppender
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender.Companion.print
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleMeteringAppender
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleTracingAppender
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleJsonConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleMeteringCollectorAppender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class Main {
    class MyBatchAppender(size: Int) : BatchAppender<LoggingEvent>(size) {
        override suspend fun handle(event: List<LoggingEvent>) {
            // E.g. send batch over http.
            println(event.joinToString { it.message })
        }
    }

    class SimpleFloodProtectedAppender(
        requestPerSecond: Int,
        burstDurationMillis: Int
    ) : FlowFloodProtectedAppender<LoggingEvent>(requestPerSecond, burstDurationMillis) {
        override suspend fun handle(event: LoggingEvent) = event.print()
    }

    private val log = Logger.ofType<SimpleLogger>(this::class)
    private val trace: Tracer = Tracer.of(this::class)
    private val meter: Meter = Meter.of(this::class)

    fun run(): Unit = runBlocking {
        log.info { "this is a test" }

        delay(1000)

        RootLogger.Metering.register(SimpleConsoleMeteringAppender())
        RootLogger.Metering.register(SimpleMeteringCollectorAppender())
        val collector = RootLogger.Metering.appenders.get(SimpleMeteringCollectorAppender::class)

        val c1 = meter.counter<Int>("event-a")
        val c2 = meter.upDownCounter<Double>("event-b")
        val g1 = meter.gauge<Int>("thread-pool-size", "a-unit", "a-description")

        delay(1000)

        c1.increment(1, "label" to "pool-a")
        c1.increment(1, "label" to "pool-a")

        c2.increment(2.0, "label" to "pool-b")
        c2.increment(2.0, "label" to "pool-b")
        c2.decrement(2.0, "label" to "pool-b")

        g1.record(3, "pool" to "pool-a")
        g1.record(6, "pool" to "pool-b")

        g1.poll(every = 2.seconds) {
            record(3, "pool" to "pool-a")
            record(6, "pool" to "pool-b")
        }

        delay(2000)
        val prometheus = collector.toOpenMetricsLineFormatString()
        print(prometheus)
        delay(2000)

        log.debug("ignore")
        log.debug { "ignore + ${5}" } // Will be evaluated only if DEBUG logs are enabled.
        log.info("this is a test")
        RootLogger.Logging.loggers.mute(Main::class)
        log.info("this is a test with 1 arg: {}", "hello")
        log.unmute()
        log.info("this is a test with 1 arg: {}", "hello")

        try {
            error("An error occurred!")
        } catch (e: Exception) {
            log.error(e) { e.message }
        }


        delay(5000)

        suspend fun <A> Iterable<A>.forEachParallel(
            context: CoroutineContext = Dispatchers.IO,
            f: suspend (A) -> Unit
        ): Unit = withContext(context) { map { async { f(it) } }.awaitAll() }

        val appender = MyBatchAppender(5)
        RootLogger.Logging.appenders.register(appender)

        (0..10).forEachParallel {
            repeat(10) {
                log.info("$it")
                delay(500)
            }
        }

        delay(2000)

        RootLogger.Tracing.register(SimpleConsoleTracingAppender())
        // Create the parent span.
        // This is useful in cases that the parent span is not created by us (e.g. from a http call).
        // NOTICE: we do not start it, since it's already started.
        val parent: TracingEvent.Span.Remote = trace.span(id = "ID_EXAMPLE", traceId = "TRACE_ID_EXAMPLE")
        // Starts immediately the span.
        trace.span("test", parent) {
            log.info(it, "this is a test with span") // The log will contain the span id.
            // Set span attributes.
            it.attributes["key"] = "value"
            // Send events that are related to the current span.
            it.event(name = "event-1", level = Level.DEBUG)
            it.debug(name = "event-1") // Same as event(name = "event-1", level = Level.DEBUG)
            // Include attributes in the event.
            it.event(name = "event-2", attrs = mapOf("key" to "value"))
            it.event(name = "event-2") { attrs ->
                attrs["key"] = "value"
            }
            // Automatically closes at the end of te scope.
        }

        // Create the span and then start it.
        val span: TracingEvent.Span.Local = trace.span("test").start()
        delay(100)
        span.event("this is a test event")
        // Close the span manually.
        span.end()

        delay(2000)

        RootLogger.Logging.appenders.unregisterAll()
        RootLogger.Logging.appenders.register(SimpleJsonConsoleLoggingAppender())
        log.info { "This is a test json log" }
        try {
            error("An error occurred!")
        } catch (e: Exception) {
            log.error(e) { e.message }
        }

        delay(2000)

        RootLogger.Logging.appenders.unregisterAll()
        RootLogger.Logging.appenders.register(
            SimpleFloodProtectedAppender(requestPerSecond = 50, burstDurationMillis = 100)
        )

        delay(2000)

        var time = measureTime {
            repeat(1_000_000) {
                log.info("$it")
            }
        }
        delay(2000)
        println("Finished in $time")

        time = measureTime {
            repeat(1_000_000) {
                log.info("$it")
            }
        }
        delay(2000)
        println("Finished in $time")
    }
}