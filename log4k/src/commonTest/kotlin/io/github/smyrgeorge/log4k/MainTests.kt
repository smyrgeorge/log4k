package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.appenders.BatchAppender
import io.github.smyrgeorge.log4k.impl.appenders.SimpleConsoleTracingAppender
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class MainTests {

    class MyBatchAppender(size: Int) : BatchAppender<LoggingEvent>(size) {
        override suspend fun append(event: List<LoggingEvent>) {
            // E.g. send batch over http.
            println(event.joinToString { it.message })
        }
    }

    private val log: Logger = Logger.of(this::class)
    private val trace: Tracer = Tracer.of(this::class)

    @Test
    fun test() {
        log.debug("ignore")
        log.debug { "ignore + ${5}" } // Will be evaluated only if DEBUG logs are enabled.
        log.info("this is a test")
        RootLogger.Logging.loggers.mute("io.github.smyrgeorge.log4k.MainTests")
        log.info("this is a test with 1 arg: {}", "hello")
        log.unmute()
        log.info("this is a test with 1 arg: {}", "hello")

        try {
            error("An error occurred!")
        } catch (e: Exception) {
            log.error(e.message)
            log.error { e.message }
            log.error(e.message, e)
            log.error(e) { e.message }
        }

        runBlocking {
            val appender = MyBatchAppender(5)
            RootLogger.Logging.appenders.register(appender)

            repeat(10) {
                log.info("$it")
                delay(500)
            }

            delay(1000)

            RootLogger.Tracing.register(SimpleConsoleTracingAppender())
            // Create the parent span.
            // NOTICE: we do not start it, since it's already started.
            val parent: TracingEvent.Span = trace.span("parent")
            // Starts immediately the span.
            trace.span("test", parent) {
                // Send events that are related to the current span.
                it.event(name = "event-1", level = Level.DEBUG)
                // Include attributes in the event.
                it.event(name = "event-2", attributes = mapOf("key" to "value"))
                it.event(name = "event-2") { attrs ->
                    attrs["key"] = "value"
                }
                // Automatically closes at the end of te scope.
            }

            // Create the span and then start it.
            val span: TracingEvent.Span = trace.span("test").start()
            span.event("this is a test event")
            span.tracer
            // Close the span manually.
            span.end()

            delay(2000)
        }
    }
}