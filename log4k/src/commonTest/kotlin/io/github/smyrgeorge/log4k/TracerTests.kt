package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.matches
import io.github.smyrgeorge.log4k.TracingEvent.Span.Status.Code
import io.github.smyrgeorge.log4k.impl.OpenTelemetryAttributes
import io.github.smyrgeorge.log4k.impl.SimpleTracer
import io.github.smyrgeorge.log4k.utils.CapturingTracingAppender
import kotlinx.coroutines.CancellationException
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
    fun aThrowingAppender_doesNotStarveTheRemainingAppenders() = runTest {
        // Register a throwing appender BEFORE the capturing one: the span must still reach the
        // capturing appender instead of being lost for every appender after the failing one.
        val throwing = object : Appender<TracingEvent> {
            override val name: String = "throwing-tracing-appender"
            override suspend fun append(event: TracingEvent): Unit = error("appender boom")
        }
        RootLogger.Tracing.appenders.unregisterAll()
        RootLogger.Tracing.appenders.register(throwing)
        RootLogger.Tracing.appenders.register(appender)

        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        tracer.span("isolation-op") { }

        assertThat(appender.awaitSpan("isolation-op").name).isEqualTo("isolation-op")
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
    fun spanBlock_nonLocalReturn_stillEndsTheSpan() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        // A non-local return exits `compute` from inside the span block, skipping both the normal
        // path and the catch — the `finally` must still end (and emit) the span.
        fun compute(flag: Boolean): Int {
            tracer.span("nonlocal-op") {
                if (flag) return 7
            }
            return 0
        }

        assertThat(compute(true)).isEqualTo(7)
        val received = appender.awaitSpan("nonlocal-op")
        assertThat(received.status.code).isEqualTo(Code.OK)
        assertThat(received.endAt).isNotNull()
    }

    @Test
    fun traced_nonLocalReturn_endsTheSpanAndRestoresTheContext() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        val context = TracingContext.create(tracer)

        fun compute(flag: Boolean): Int {
            TracingContext.traced(context = context, parent = null, tracer = null, name = "traced-nonlocal") {
                if (flag) return 7
            }
            return 0
        }

        assertThat(compute(true)).isEqualTo(7)
        val received = appender.awaitSpan("traced-nonlocal")
        assertThat(received.status.code).isEqualTo(Code.OK)
        assertThat(received.endAt).isNotNull()
        // The context's current span must be restored (back to the null root) as well.
        assertThat(context.currentOrNull()).isNull()
    }

    @Test
    fun exception_respectsSpanLifecycle() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        // On a never-started span the exception is ignored.
        val unstarted = tracer.span("exc-unstarted")
        unstarted.exception(IllegalStateException("early"))
        assertThat(unstarted.events).isEmpty()

        // On an open span it is recorded (exceptions bypass the level filter).
        val open = tracer.span("exc-open").start()
        open.exception(IllegalStateException("mid"))
        assertThat(open.events.map { it.name }).contains(OpenTelemetryAttributes.EXCEPTION)

        // After `end()` the span has already been handed to the appenders: a late exception must
        // not mutate it anymore.
        open.end()
        open.exception(IllegalStateException("late"))
        assertThat(open.events.size).isEqualTo(1)
    }

    @Test
    fun startedSpan_endsAndEmits_evenIfTracerIsMutedMidFlight() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        val span = tracer.span("mid-flight-mute").start()
        tracer.mute() // raises the tracer level to OFF while the span is in flight
        span.end()

        // The record/drop decision was made at start: the span still closes and is emitted,
        // instead of being silently dropped and left half-open.
        val received = appender.awaitSpan("mid-flight-mute")
        assertThat(received.endAt).isNotNull()
        assertThat(received.status.code).isEqualTo(Code.OK)
    }

    @Test
    fun restoreCurrent_doesNotClobberAConcurrentSiblingSpan() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        val context = TracingContext.create(tracer)
        val parent = tracer.span("ctx-parent").start()
        context.current = parent

        // Two siblings interleaving on one shared context: B replaces A as the current span.
        val a = tracer.span("ctx-a", parent).start()
        context.current = a
        val b = tracer.span("ctx-b", parent).start()
        context.current = b

        // A finishes first: its compare-and-restore must not clobber B's slot...
        context.restoreCurrent(expected = a, to = parent)
        assertThat(context.currentOrNull()).isSameInstanceAs(b)

        // ...and when B finishes, the parent is restored.
        context.restoreCurrent(expected = b, to = parent)
        assertThat(context.currentOrNull()).isSameInstanceAs(parent)
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
    fun ids_areNeverTheAllZeroInvalidSentinel() {
        // W3C Trace Context / OpenTelemetry reserve all-zero IDs as the invalid/null sentinel.
        repeat(100) {
            assertThat(Tracer.spanId()).isNotEqualTo("0000000000000000")
            assertThat(Tracer.traceId()).isNotEqualTo("0".repeat(32))
        }
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

    // --- Mute / OFF gating of new spans ---------------------------------------------------------

    @Test
    fun mutedTracer_doesNotStartNorEmitNewSpans_andUnmuteRestores() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.INFO)

        // A span created while the tracer is muted snapshots OFF as its level: it must neither
        // start nor be emitted (before the fix, `OFF >= OFF` started — and emitted — it).
        tracer.mute()
        val muted = tracer.span("muted-span").start()
        assertThat(muted.startAt).isNull()
        muted.end()

        // After unmuting, new spans flow again — and must be the first we see from this test,
        // proving the muted span produced nothing before them.
        tracer.unmute()
        val after = tracer.span("unmuted-span").start()
        after.end()

        val first = appender.awaitEvent {
            it is TracingEvent.Span && (it.name == "muted-span" || it.name == "unmuted-span")
        } as TracingEvent.Span
        assertThat(first.name).isEqualTo("unmuted-span")
    }

    @Test
    fun offLevelTracer_neverStartsNorEmitsSpans() = runTest {
        val off = SimpleTracer("test.tracer.off", Level.OFF)

        val span = off.span("off-span").start()
        assertThat(span.startAt).isNull()
        span.end()
        span.event("ignored") // must be a no-op on a never-started span

        // A marker from a healthy tracer must be the first span we see.
        SimpleTracer("test.tracer.marker", Level.INFO).span("off-marker") { }

        val first = appender.awaitEvent {
            it is TracingEvent.Span && (it.name == "off-span" || it.name == "off-marker")
        } as TracingEvent.Span
        assertThat(first.name).isEqualTo("off-marker")
        assertThat(span.events).isEmpty()
    }

    // --- Cancellation is not a failure ----------------------------------------------------------

    @Test
    fun spanBlock_cancellation_endsSpanWithoutRecordingAFailure() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        assertFailsWith<CancellationException> {
            tracer.span<Unit>("cancel-op") { throw CancellationException("cancelled") }
        }

        // Cancellation is normal control flow (mirrors Meter.Timed.measure): the span still ends —
        // never left half-open — but carries no exception event and no ERROR status.
        val received = appender.awaitSpan("cancel-op")
        assertThat(received.endAt).isNotNull()
        assertThat(received.status.code).isEqualTo(Code.OK)
        assertThat(received.status.error).isNull()
        assertThat(received.events).isEmpty()
    }

    @Test
    fun traced_cancellation_endsSpanWithoutFailure_andRestoresTheContext() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)
        val context = TracingContext.create(tracer)

        assertFailsWith<CancellationException> {
            TracingContext.traced<Unit>(context = context, parent = null, tracer = null, name = "traced-cancel") {
                throw CancellationException("cancelled")
            }
        }

        val received = appender.awaitSpan("traced-cancel")
        assertThat(received.endAt).isNotNull()
        assertThat(received.status.code).isEqualTo(Code.OK)
        assertThat(received.events).isEmpty()
        // The context's current span must be restored (back to the null root) as well.
        assertThat(context.currentOrNull()).isNull()
    }

    // --- Lazy-tag event helpers -----------------------------------------------------------------

    @Test
    fun levelNamedEventHelpers_populateTheEventTags() = runTest {
        val tracer = SimpleTracer("test.tracer", Level.TRACE)

        // The lambda receives the event's MutableTags to populate (this did not even compile while
        // the helpers were typed `(Tags) -> Unit`).
        tracer.span("evt-tags") {
            trace("t") { it["k"] = "vt" }
            debug("d") { it["k"] = "vd" }
            info("i") { it["k"] = "vi" }
            warn("w") { it["k"] = "vw" }
            error("e") { it["k"] = "ve" }
        }

        val span = appender.awaitSpan("evt-tags")
        val byName = span.events.associate { it.name to it.tags }
        assertThat(byName["t"]).isEqualTo(mapOf<String, Any>("k" to "vt"))
        assertThat(byName["d"]).isEqualTo(mapOf<String, Any>("k" to "vd"))
        assertThat(byName["i"]).isEqualTo(mapOf<String, Any>("k" to "vi"))
        assertThat(byName["w"]).isEqualTo(mapOf<String, Any>("k" to "vw"))
        assertThat(byName["e"]).isEqualTo(mapOf<String, Any>("k" to "ve"))
    }
}
