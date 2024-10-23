package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleTracingAppender
import kotlin.test.Test

class MainTests {
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
            log.error(e) { e.message }
        }

        RootLogger.Tracing.register(SimpleConsoleTracingAppender())
        // Create the parent span.
        // NOTICE: we do not start it, since it's already started.
        val parent: TracingEvent.Span = trace.span(id = "ID_EXAMPLE", traceId = "TRACE_ID_EXAMPLE", name = "parent")
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
        val span: TracingEvent.Span = trace.span("test").start()
        span.event("this is a test event")
        // Close the span manually.
        span.end()
    }
}