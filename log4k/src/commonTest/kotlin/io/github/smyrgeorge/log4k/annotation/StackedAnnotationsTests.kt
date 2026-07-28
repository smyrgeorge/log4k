package io.github.smyrgeorge.log4k.annotation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.startsWith
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.TracingEvent.Span.Status.Code
import io.github.smyrgeorge.log4k.impl.SimpleTracer
import io.github.smyrgeorge.log4k.utils.CapturingLoggingAppender
import io.github.smyrgeorge.log4k.utils.CapturingMeteringAppender
import io.github.smyrgeorge.log4k.utils.CapturingTracingAppender
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

// --- Fixtures instrumented by the log4k-compiler-plugin (wired onto the test compilations) --------
//
// Every fixture stacks two or all three instrumentation annotations on the same declaration. This
// file *compiling* is the first assertion: stacking must not raise a compile error. The tests then
// verify that each pass really wrapped the function — every stacked signal is emitted at runtime.

private class StackedFixture {
    @Logged
    @Traced
    @Timed
    fun compute(x: Int): Int = x * x // logged + span + metrics, all named "StackedFixture.compute"

    @Logged(level = Level.DEBUG, tags = [Tag("component", "billing")])
    @Traced(name = "stacked-op", tags = [Tag("component", "billing")])
    @Timed(name = "stacked.op", tags = [Tag("component", "billing")])
    context(_: TracingContext)
    suspend fun load(id: Long): String { // suspend + context param + per-annotation config
        delay(1.milliseconds)
        return "user-$id"
    }

    @Logged
    @Traced
    @Timed
    fun boom(): Nothing = error("kaboom") // failure must propagate through all three wrappers

    @Logged
    @Timed
    fun pair(x: Int): Int = x + 1 // two-annotation combination (no tracing)
}

@Logged
@Traced
@Timed
private class StackedClassFixture {
    fun ping(): String = "pong" // class-level stacking instruments eligible members with all three

    @NoTrace
    fun quiet(): Int = 1 // facet opt-out must only silence its own signal
}

/**
 * End-to-end tests for **stacking** [Logged] / [Traced] / [Timed] on the same function or class. The
 * compiler plugin runs one IR pass per annotation, each wrapping the body produced by the previous
 * one (`logged { measure { span { body } } }`), so all combinations must compile and every stacked
 * signal must be emitted through its `RootLogger -> Channel -> appender` pipeline.
 */
class StackedAnnotationsTests {

    private lateinit var logging: CapturingLoggingAppender
    private lateinit var tracing: CapturingTracingAppender
    private lateinit var metering: CapturingMeteringAppender

    private var savedLogging: List<Appender<LoggingEvent>> = emptyList()
    private var savedTracing: List<Appender<TracingEvent>> = emptyList()
    private var savedMetering: List<Appender<MeteringEvent>> = emptyList()
    private var savedLoggingLevel: Level = Level.INFO
    private var savedTracingLevel: Level = Level.INFO

    @BeforeTest
    fun setup() {
        savedLoggingLevel = RootLogger.Logging.level
        savedTracingLevel = RootLogger.Tracing.level
        RootLogger.Logging.level = Level.TRACE // ensure the synthesized `_log_` never gates a line
        RootLogger.Tracing.level = Level.TRACE // ensure the synthesized `_trace_` never gates a span

        savedLogging = RootLogger.Logging.appenders.all()
        savedTracing = RootLogger.Tracing.appenders.all()
        savedMetering = RootLogger.Metering.appenders.all()
        RootLogger.Logging.appenders.unregisterAll()
        RootLogger.Tracing.appenders.unregisterAll()
        RootLogger.Metering.appenders.unregisterAll()

        logging = CapturingLoggingAppender()
        tracing = CapturingTracingAppender()
        metering = CapturingMeteringAppender()
        RootLogger.Logging.appenders.register(logging)
        RootLogger.Tracing.appenders.register(tracing)
        RootLogger.Metering.appenders.register(metering)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Logging.appenders.unregisterAll()
        RootLogger.Tracing.appenders.unregisterAll()
        RootLogger.Metering.appenders.unregisterAll()
        savedLogging.forEach { RootLogger.Logging.appenders.register(it) }
        savedTracing.forEach { RootLogger.Tracing.appenders.register(it) }
        savedMetering.forEach { RootLogger.Metering.appenders.register(it) }
        RootLogger.Logging.level = savedLoggingLevel
        RootLogger.Tracing.level = savedTracingLevel
    }

    @Test
    fun allThreeOnFunction_emitLogsSpanAndMetrics() = runTest {
        val result = StackedFixture().compute(5)

        assertThat(result).isEqualTo(25)

        val events = logging.awaitEvents(2) { it.message.contains("StackedFixture.compute") }
        assertThat(events[0].message).isEqualTo("→ StackedFixture.compute(x=5)")
        assertThat(events[1].message).startsWith("← StackedFixture.compute = 25 (")

        val span = tracing.awaitSpan("StackedFixture.compute")
        assertThat(span.status.code).isEqualTo(Code.OK)

        val calls = metering.awaitValue("StackedFixture.compute.calls")
        assertThat(calls).isInstanceOf(MeteringEvent.Increment::class)
        assertThat(calls.value).isEqualTo(1L)
        val duration = metering.awaitValue("StackedFixture.compute.duration")
        assertThat(duration).isInstanceOf(MeteringEvent.Record::class)
    }

    @Test
    fun allThreeOnSuspendFunction_applyEachAnnotationsOwnConfig() = runTest {
        val tracer = SimpleTracer("test.anno.tracer", Level.TRACE)

        val result = with(TracingContext.create(tracer = tracer)) { StackedFixture().load(7) }

        assertThat(result).isEqualTo("user-7")

        // @Logged config: DEBUG level + its own tags; the log name stays "ClassName.functionName".
        val events = logging.awaitEvents(2) { it.message.contains("StackedFixture.load") }
        assertThat(events[0].level).isEqualTo(Level.DEBUG)
        assertThat(events[0].message).isEqualTo("→ StackedFixture.load(id=7)")
        assertThat(events[0].tags).isEqualTo(mapOf<String, Any>("component" to "billing"))

        // @Traced config: custom span name + tags.
        val span = tracing.awaitSpan("stacked-op")
        assertThat(span.status.code).isEqualTo(Code.OK)
        assertThat(span.tags["component"]).isEqualTo("billing")

        // @Timed config: custom metric base name + dimensions.
        val calls = metering.awaitValue("stacked.op.calls")
        assertThat(calls.tags["component"]).isEqualTo("billing")
    }

    @Test
    fun allThreeOnFunction_exceptionPropagatesThroughEveryWrapper() = runTest {
        val thrown = assertFailsWith<IllegalStateException> { StackedFixture().boom() }

        assertThat(thrown.message).isEqualTo("kaboom")

        val events = logging.awaitEvents(2) { it.message.contains("StackedFixture.boom") }
        assertThat(events[1].level).isEqualTo(Level.ERROR)
        assertThat(events[1].message).startsWith("✗ StackedFixture.boom failed (")
        assertThat(events[1].throwable).isSameInstanceAs(thrown)

        val span = tracing.awaitSpan("StackedFixture.boom")
        assertThat(span.status.code).isEqualTo(Code.ERROR)

        val errors = metering.awaitValue("StackedFixture.boom.errors")
        assertThat(errors).isInstanceOf(MeteringEvent.Increment::class)
        assertThat(errors.value).isEqualTo(1L)
    }

    @Test
    fun loggedPlusTimed_worksWithoutTracing() = runTest {
        val result = StackedFixture().pair(1)

        assertThat(result).isEqualTo(2)
        val events = logging.awaitEvents(2) { it.message.contains("StackedFixture.pair") }
        assertThat(events[0].message).isEqualTo("→ StackedFixture.pair(x=1)")
        val calls = metering.awaitValue("StackedFixture.pair.calls")
        assertThat(calls).isInstanceOf(MeteringEvent.Increment::class)
    }

    @Test
    fun allThreeOnClass_instrumentEligibleMembersWithEverySignal() = runTest {
        val result = StackedClassFixture().ping()

        assertThat(result).isEqualTo("pong")
        val events = logging.awaitEvents(2) { it.message.contains("StackedClassFixture.ping") }
        assertThat(events[0].message).isEqualTo("→ StackedClassFixture.ping()")
        val span = tracing.awaitSpan("StackedClassFixture.ping")
        assertThat(span.status.code).isEqualTo(Code.OK)
        val calls = metering.awaitValue("StackedClassFixture.ping.calls")
        assertThat(calls.value).isEqualTo(1L)
    }

    @Test
    fun facetOptOut_onStackedClass_onlySilencesItsOwnSignal() = runTest {
        val fixture = StackedClassFixture()
        fixture.quiet() // @NoTrace -> no span, but still logged + timed
        fixture.ping()  // marker span

        val events = logging.awaitEvents(2) { it.message.contains("StackedClassFixture.quiet") }
        assertThat(events[0].message).isEqualTo("→ StackedClassFixture.quiet()")
        val calls = metering.awaitValue("StackedClassFixture.quiet.calls")
        assertThat(calls.value).isEqualTo(1L)

        // The first span from this class must be the marker's — `quiet` produced none.
        val first = tracing.awaitEvent {
            it is TracingEvent.Span &&
                    (it.name == "StackedClassFixture.quiet" || it.name == "StackedClassFixture.ping")
        } as TracingEvent.Span
        assertThat(first.name).isEqualTo("StackedClassFixture.ping")
    }
}
