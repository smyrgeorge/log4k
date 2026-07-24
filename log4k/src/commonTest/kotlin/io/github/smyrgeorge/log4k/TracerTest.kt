package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import io.github.smyrgeorge.log4k.TracingEvent.Span.Status.Code
import io.github.smyrgeorge.log4k.impl.OpenTelemetryAttributes
import io.github.smyrgeorge.log4k.impl.SimpleTracer
import io.github.smyrgeorge.log4k.utils.CapturingTracingAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Integration tests for [Tracer]. Each test registers a [CapturingTracingAppender] and drives real
 * spans, then asserts on what actually flowed through the `RootLogger -> Channel -> appender` pipeline
 * (a span only reaches the appender once `Local.end()` emits it and the tracing queue is consumed).
 *
 * Delivery is asynchronous, so the tests run inside [runTest] and suspend on `awaitSpan(...)` until the
 * span in question has been appended.
 */
class TracerTest {

    private lateinit var appender: CapturingTracingAppender

    // Tracing has no default appender, but other test classes may leave one registered on the global
    // RootLogger. Detach whatever is there, install only our capturing appender for the test, and
    // restore the original set afterwards so tests stay isolated.
    private var saved: List<Appender<TracingEvent>> = emptyList()

    @BeforeTest
    fun setup() {
        saved = RootLogger.Tracing.appenders.all()
        RootLogger.Tracing.appenders.unregisterAll()
        appender = CapturingTracingAppender()
        RootLogger.Tracing.appenders.register(appender)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Tracing.appenders.unregisterAll()
        saved.forEach { RootLogger.Tracing.appenders.register(it) }
    }

    @Test
    fun startedSpan_isDeliveredWhenEnded() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val span = tracer.span("start-end").start()
        span.end()

        val received = appender.awaitSpan("start-end")
        assertThat(received.name).isEqualTo("start-end")
        assertThat(received.startAt).isNotNull()
        assertThat(received.endAt).isNotNull()
        assertThat(received.status.code).isEqualTo(Code.OK)
        assertThat(received.status.error).isNull()
    }

    @Test
    fun spanBlock_returnsResultAndEndsOk() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val result = tracer.span("block-op") { 21 * 2 }

        assertThat(result).isEqualTo(42)
        val received = appender.awaitSpan("block-op")
        assertThat(received.status.code).isEqualTo(Code.OK)
        assertThat(received.endAt).isNotNull()
    }

    @Test
    fun spanBlock_whenBodyThrows_marksErrorRecordsExceptionAndRethrows() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        val boom = IllegalStateException("kaboom")

        val thrown = assertFailsWith<IllegalStateException> {
            tracer.span<Unit>("boom-op") { throw boom }
        }

        assertThat(thrown).isSameInstanceAs(boom)
        val received = appender.awaitSpan("boom-op")
        assertThat(received.status.code).isEqualTo(Code.ERROR)
        assertThat(received.status.error).isSameInstanceAs(boom)
        assertThat(received.status.description).isEqualTo("kaboom")
        // The inline span helper records an exception event before ending the span.
        assertThat(received.events.map { it.name }).contains(OpenTelemetryAttributes.EXCEPTION)
    }

    @Test
    fun childSpan_inheritsParentTraceIdButHasOwnSpanId() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val parent = tracer.span("parent-op").start()
        tracer.span("child-op", parent = parent) { }

        val child = appender.awaitSpan("child-op")
        assertThat(child.parent).isSameInstanceAs(parent)
        assertThat(child.context.traceId).isEqualTo(parent.context.traceId)
        assertThat(child.context.spanId).isNotEqualTo(parent.context.spanId)
    }

    @Test
    fun remoteParent_propagatesTraceIdToLocalChild() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val remote = tracer.span(id = "remote-1", traceId = "trace-xyz", name = "remote-parent")
        tracer.span("local-child", parent = remote) { }

        val child = appender.awaitSpan("local-child")
        assertThat(child.parent).isSameInstanceAs(remote)
        assertThat(child.context.traceId).isEqualTo("trace-xyz")
    }

    @Test
    fun span_carriesTags() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        tracer.span("tagged-op", tags = mapOf("component" to "billing")) { }

        val received = appender.awaitSpan("tagged-op")
        assertThat(received.tags["component"]).isEqualTo("billing")
    }

    @Test
    fun span_recordsEventsAndTagsAddedInsideBlock() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        tracer.span("evented-op") {
            event(name = "checkpoint", level = Level.INFO)
            tags["k"] = "v"
        }

        val received = appender.awaitSpan("evented-op")
        assertThat(received.events.map { it.name }).contains("checkpoint")
        assertThat(received.tags["k"]).isEqualTo("v")
    }
}
