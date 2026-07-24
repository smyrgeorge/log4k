package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.matches
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
class TracerTests {

    private class SampleForTracerFactory

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

    // --- Companion: id generation & factory ----------------------------------------------------

    @Test
    fun spanId_is16LowercaseHexChars() {
        assertThat(Tracer.spanId()).matches(Regex("[0-9a-f]{16}"))
    }

    @Test
    fun traceId_is32LowercaseHexChars() {
        assertThat(Tracer.traceId()).matches(Regex("[0-9a-f]{32}"))
    }

    @Test
    fun of_byName_returnsSimpleTracer_andCachesByName() {
        val a = Tracer.of("test.tracer.factory.ByName")
        val b = Tracer.of("test.tracer.factory.ByName")
        assertThat(a).isInstanceOf(SimpleTracer::class)
        assertThat(a.name).isEqualTo("test.tracer.factory.ByName")
        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun of_byClass_cachesInstance() {
        val a = Tracer.of(SampleForTracerFactory::class)
        val b = Tracer.of(SampleForTracerFactory::class)
        assertThat(a).isSameInstanceAs(b)
        assertThat(a.name).isNotEmpty()
    }

    // --- Span lifecycle edge cases -------------------------------------------------------------

    @Test
    fun start_isIdempotent_startAtNotReset() {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        val span = tracer.span("idempotent-start").start()
        val firstStart = span.startAt
        span.start()
        assertThat(span.startAt).isEqualTo(firstStart)
    }

    @Test
    fun endWithoutStart_emitsNothing() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val notStarted = tracer.span("never-started")
        notStarted.end() // no-op: the span was never started
        tracer.span("start-marker") { }

        // If the un-started span had emitted, it would precede the marker.
        val first = appender.awaitEvent {
            it is TracingEvent.Span && (it.name == "never-started" || it.name == "start-marker")
        } as TracingEvent.Span
        assertThat(first.name).isEqualTo("start-marker")
    }

    @Test
    fun end_isIdempotent_emitsOnce() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val span = tracer.span("end-once").start()
        span.end()
        span.end() // second end is a no-op

        appender.awaitSpan("end-once")
        tracer.span("end-once-marker") { }

        // A second emission of "end-once" would appear before the marker.
        val next = appender.awaitEvent {
            it is TracingEvent.Span && (it.name == "end-once" || it.name == "end-once-marker")
        } as TracingEvent.Span
        assertThat(next.name).isEqualTo("end-once-marker")
    }

    @Test
    fun spanEvent_belowSpanLevel_isDropped_atOrAboveIsKept() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.INFO) // the span inherits the tracer level (INFO)

        tracer.span("event-gate") {
            event("below", Level.DEBUG) // dropped: DEBUG < INFO
            event("at", Level.INFO)     // kept
            event("above", Level.WARN)  // kept
        }

        val span = appender.awaitSpan("event-gate")
        val names = span.events.map { it.name }
        assertThat(names).contains("at")
        assertThat(names).contains("above")
        assertThat(names).doesNotContain("below")
    }

    @Test
    fun exception_recordsOpenTelemetryAttributes() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        val boom = IllegalStateException("bang")

        assertFailsWith<IllegalStateException> {
            tracer.span<Unit>("otel-exc") { throw boom }
        }

        val span = appender.awaitSpan("otel-exc")
        val exception = span.events.first { it.name == OpenTelemetryAttributes.EXCEPTION }
        assertThat(exception.tags[OpenTelemetryAttributes.EXCEPTION_MESSAGE]).isEqualTo("bang")
        assertThat(exception.tags[OpenTelemetryAttributes.EXCEPTION_TYPE]).isNotNull()
        assertThat(exception.tags[OpenTelemetryAttributes.EXCEPTION_STACKTRACE]).isNotNull()
    }

    @Test
    fun explicitEndWithError_marksErrorStatus() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        val boom = RuntimeException("manual")

        val span = tracer.span("manual-error").start()
        span.end(boom)

        val received = appender.awaitSpan("manual-error")
        assertThat(received.status.code).isEqualTo(Code.ERROR)
        assertThat(received.status.error).isSameInstanceAs(boom)
        assertThat(received.status.description).isEqualTo("manual")
    }

    @Test
    fun spanScopedChild_viaSpanDotSpan_inheritsTrace() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val parent = tracer.span("scoped-parent").start()
        parent.span("scoped-child") { } // child created from the parent span itself

        val child = appender.awaitSpan("scoped-child")
        assertThat(child.parent).isSameInstanceAs(parent)
        assertThat(child.context.traceId).isEqualTo(parent.context.traceId)
    }

    @Test
    fun convenienceEventMethods_recordAtTraceLevel() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE) // everything is kept at TRACE

        tracer.span("convenience") {
            trace("t")
            debug("d")
            info("i")
            warn("w")
            error("e")
        }

        val span = appender.awaitSpan("convenience")
        val names = span.events.map { it.name }
        assertThat(names).contains("t")
        assertThat(names).contains("d")
        assertThat(names).contains("i")
        assertThat(names).contains("w")
        assertThat(names).contains("e")
    }
}
