package io.github.smyrgeorge.log4k.annotation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.TracingEvent.Span.Status.Code
import io.github.smyrgeorge.log4k.impl.SimpleTracer
import io.github.smyrgeorge.log4k.utils.CapturingTracingAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

// --- Fixtures instrumented by the log4k-compiler-plugin (wired onto the test compilations) --------

@Traced(tags = [Tag("layer", "service")])
private class TracedFixture {
    context(_: TracingContext)
    fun load(id: Long): String = "user-$id" // span "TracedFixture.load", nested under the context

    fun helper(): Int = 7 // no context -> ROOT span "TracedFixture.helper" via synthesized `_trace_`

    @NoTrace
    context(_: TracingContext)
    fun secret(): String = "shh" // opted out
}

@NoTrace
private class SilencedTracedFixture {
    @Traced
    fun ignored(): Int = 1 // class-level @NoTrace kill switch -> not traced despite @Traced
}

@Traced(name = "child-op")
context(_: TracingEvent.Span.Local)
private fun tracedChild(): String = "done" // parent = the Span.Local in scope

@Traced(name = "boom-op")
context(_: TracingContext)
private fun tracedBoom() {
    error("kaboom")
}

@Traced(name = "explicit-op", tags = [Tag("component", "billing")])
context(_: TracingContext)
private fun tracedExplicit(): Int = 5

/**
 * End-to-end tests for the [Traced] / [NoTrace] annotations: the compiler plugin is applied to the
 * test compilations, so the fixtures above are really instrumented. Each test drives a fixture and
 * asserts on the span that flowed through the `RootLogger -> Channel -> appender` pipeline.
 */
class TracedTests {

    private lateinit var appender: CapturingTracingAppender
    private var saved: List<Appender<TracingEvent>> = emptyList()
    private var savedLevel: Level = Level.INFO

    @BeforeTest
    fun setup() {
        savedLevel = RootLogger.Tracing.level
        RootLogger.Tracing.level = Level.TRACE // ensure synthesized tracers never gate a span
        saved = RootLogger.Tracing.appenders.all()
        RootLogger.Tracing.appenders.unregisterAll()
        appender = CapturingTracingAppender()
        RootLogger.Tracing.appenders.register(appender)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Tracing.appenders.unregisterAll()
        saved.forEach { RootLogger.Tracing.appenders.register(it) }
        RootLogger.Tracing.level = savedLevel
    }

    @Test
    fun classLevelTraced_memberWithContext_isNestedAndTagged() = runTest {
        val tracer = SimpleTracer("test.anno.tracer", Level.TRACE)

        val result = with(TracingContext.create(tracer = tracer)) { TracedFixture().load(7) }

        assertThat(result).isEqualTo("user-7")
        val span = appender.awaitSpan("TracedFixture.load")
        assertThat(span.status.code).isEqualTo(Code.OK)
        assertThat(span.tags["layer"]).isEqualTo("service") // class-level tag materialized
    }

    @Test
    fun classLevelTraced_memberWithoutContext_createsRootSpanViaSynthesizedTracer() = runTest {
        val result = TracedFixture().helper()

        assertThat(result).isEqualTo(7)
        val span = appender.awaitSpan("TracedFixture.helper")
        assertThat(span.parent).isNull() // root span
        assertThat(span.tags["layer"]).isEqualTo("service")
    }

    @Test
    fun noTrace_onFunction_optsOut() = runTest {
        val tracer = SimpleTracer("test.anno.tracer", Level.TRACE)

        with(TracingContext.create(tracer = tracer)) {
            val fixture = TracedFixture()
            fixture.secret()  // @NoTrace -> no span
            fixture.load(1)   // marker -> "TracedFixture.load"
        }

        val first = appender.awaitEvent {
            it is TracingEvent.Span && (it.name == "TracedFixture.secret" || it.name == "TracedFixture.load")
        } as TracingEvent.Span
        assertThat(first.name).isEqualTo("TracedFixture.load")
    }

    @Test
    fun noTrace_onClass_disablesEverything() = runTest {
        val tracer = SimpleTracer("test.anno.tracer", Level.TRACE)

        SilencedTracedFixture().ignored() // class @NoTrace -> nothing, even though method is @Traced
        with(TracingContext.create(tracer = tracer)) { TracedFixture().load(2) }

        val first = appender.awaitEvent {
            it is TracingEvent.Span && (it.name == "SilencedTracedFixture.ignored" || it.name == "TracedFixture.load")
        } as TracingEvent.Span
        assertThat(first.name).isEqualTo("TracedFixture.load")
    }

    @Test
    fun traced_withSpanInScope_usesItAsParent() = runTest {
        val tracer = SimpleTracer("test.anno.tracer", Level.TRACE)

        tracer.span("parent-op") {
            tracedChild() // parent = this Span.Local
        }

        val child = appender.awaitSpan("child-op")
        assertThat(child.parent).isNotNull()
        assertThat(child.parent!!.name).isEqualTo("parent-op")
    }

    @Test
    fun traced_exceptionPath_marksErrorAndRethrows() = runTest {
        val tracer = SimpleTracer("test.anno.tracer", Level.TRACE)

        val thrown = assertFailsWith<IllegalStateException> {
            with(TracingContext.create(tracer = tracer)) { tracedBoom() }
        }

        assertThat(thrown.message).isEqualTo("kaboom")
        val span = appender.awaitSpan("boom-op")
        assertThat(span.status.code).isEqualTo(Code.ERROR)
    }

    @Test
    fun traced_explicitNameAndTags_areApplied() = runTest {
        val tracer = SimpleTracer("test.anno.tracer", Level.TRACE)

        val result = with(TracingContext.create(tracer = tracer)) { tracedExplicit() }

        assertThat(result).isEqualTo(5)
        val span = appender.awaitSpan("explicit-op")
        assertThat(span.tags["component"]).isEqualTo("billing")
    }
}
