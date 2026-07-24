package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import io.github.smyrgeorge.log4k.classic.debug
import io.github.smyrgeorge.log4k.classic.error
import io.github.smyrgeorge.log4k.classic.info
import io.github.smyrgeorge.log4k.classic.trace
import io.github.smyrgeorge.log4k.classic.warn
import io.github.smyrgeorge.log4k.impl.SimpleLogger
import io.github.smyrgeorge.log4k.utils.CapturingLoggingAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Integration tests for [Logger]. Each test registers a [CapturingLoggingAppender] and drives a real
 * [SimpleLogger], then asserts on what actually flowed through the `RootLogger -> Channel -> appender`
 * pipeline (an event only reaches the appender once [Logger.log] builds it — after the level gate —
 * and the logging queue is consumed).
 *
 * Delivery is asynchronous, so the tests run inside [runTest] and suspend on `awaitEvent(...)` /
 * `awaitEvents(...)` until the event(s) in question have been appended. Suppression ("no event")
 * cases are proven deterministically by ordering against a marker log rather than with a timeout.
 */
class LoggerTest {

    private class SampleForFactory

    private lateinit var appender: CapturingLoggingAppender

    // RootLogger registers a default console appender; other test classes may add their own. Detach
    // whatever is there, install only our capturing appender for the test (which also keeps output
    // clean), and restore the original set afterwards so tests stay isolated.
    private var saved: List<Appender<LoggingEvent>> = emptyList()

    @BeforeTest
    fun setup() {
        saved = RootLogger.Logging.appenders.all()
        RootLogger.Logging.appenders.unregisterAll()
        appender = CapturingLoggingAppender()
        RootLogger.Logging.appenders.register(appender)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Logging.appenders.unregisterAll()
        saved.forEach { RootLogger.Logging.appenders.register(it) }
    }

    // --- Level gating (log() + enabled()) ------------------------------------------------------

    @Test
    fun log_belowThreshold_emitsNoEvent() = runTest {
        val logger = SimpleLogger("test.gate.below", Level.INFO)

        logger.log(Level.DEBUG, null, "debug msg", emptyArray(), null) // suppressed
        logger.log(Level.INFO, null, "marker", emptyArray(), null)     // emitted

        // If DEBUG had not been gated, it would have been the first event from this logger.
        val first = appender.awaitEvent { it.logger == "test.gate.below" }
        assertThat(first.level).isEqualTo(Level.INFO)
        assertThat(first.message).isEqualTo("marker")
    }

    @Test
    fun log_atThreshold_emitsEvent() = runTest {
        val logger = SimpleLogger("test.gate.at", Level.INFO)

        logger.log(Level.INFO, null, "info msg", emptyArray(), null)

        val event = appender.awaitEvent { it.logger == "test.gate.at" }
        assertThat(event.level).isEqualTo(Level.INFO)
        assertThat(event.message).isEqualTo("info msg")
    }

    @Test
    fun log_aboveThreshold_emitsEvent() = runTest {
        val logger = SimpleLogger("test.gate.above", Level.INFO)

        logger.log(Level.ERROR, null, "boom", emptyArray(), null)

        val event = appender.awaitEvent { it.logger == "test.gate.above" }
        assertThat(event.level).isEqualTo(Level.ERROR)
    }

    @Test
    fun log_whenLevelOff_emitsNothingEvenForError() = runTest {
        val off = SimpleLogger("test.gate.off", Level.OFF)
        val marker = SimpleLogger("test.gate.off.marker", Level.INFO)

        off.log(Level.ERROR, null, "boom", emptyArray(), null) // suppressed (OFF)
        marker.log(Level.INFO, null, "marker", emptyArray(), null)

        // The OFF logger must have emitted nothing before the marker.
        val first = appender.awaitEvent {
            it.logger == "test.gate.off" || it.logger == "test.gate.off.marker"
        }
        assertThat(first.logger).isEqualTo("test.gate.off.marker")
    }

    @Test
    fun isEnabled_reflectsConfiguredThreshold() {
        val logger = SimpleLogger("test.enabled", Level.WARN)
        assertThat(logger.isEnabled(Level.TRACE)).isFalse()
        assertThat(logger.isEnabled(Level.DEBUG)).isFalse()
        assertThat(logger.isEnabled(Level.INFO)).isFalse()
        assertThat(logger.isEnabled(Level.WARN)).isTrue()
        assertThat(logger.isEnabled(Level.ERROR)).isTrue()
    }

    // --- Event construction (fields carried through the pipeline) ------------------------------

    @Test
    fun log_propagatesAllFieldsToLoggingEvent() = runTest {
        val logger = SimpleLogger("my.logger", Level.TRACE)
        val ex = RuntimeException("x")
        val span = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")

        logger.log(Level.WARN, span, "msg {}", arrayOf<Any?>("a", 1), ex)

        val event = appender.awaitEvent { it.logger == "my.logger" }
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.message).isEqualTo("msg {}")
        assertThat(event.arguments.toList()).containsExactly("a", 1)
        assertThat(event.throwable).isSameInstanceAs(ex)
        assertThat(event.span).isSameInstanceAs(span)
    }

    @Test
    fun log_withoutSpanOrThrowable_leavesThemNull() = runTest {
        val logger = SimpleLogger("test.nulls", Level.TRACE)

        logger.log(Level.INFO, null, "m", emptyArray(), null)

        val event = appender.awaitEvent { it.logger == "test.nulls" }
        assertThat(event.span).isNull()
        assertThat(event.throwable).isNull()
    }

    // --- Classic extension API integration -----------------------------------------------------

    @Test
    fun classicInfo_emitsSingleInfoEvent() = runTest {
        val logger = SimpleLogger("test.classic.info", Level.TRACE)

        logger.info("hello world")

        val event = appender.awaitEvent { it.logger == "test.classic.info" }
        assertThat(event.level).isEqualTo(Level.INFO)
        assertThat(event.message).isEqualTo("hello world")
        assertThat(event.throwable).isNull()
        assertThat(event.arguments.toList()).isEmpty()
    }

    @Test
    fun classicMessage_keepsPlaceholdersAndArgumentsVerbatim() = runTest {
        val logger = SimpleLogger("test.classic.args", Level.TRACE)

        logger.info("user {} logged in from {}", "alice", "127.0.0.1")

        // log4k does not interpolate placeholders at the Logger layer; the raw message and the
        // arguments are carried through untouched for a downstream appender to render.
        val event = appender.awaitEvent { it.logger == "test.classic.args" }
        assertThat(event.message).isEqualTo("user {} logged in from {}")
        assertThat(event.arguments.toList()).containsExactly("alice", "127.0.0.1")
    }

    @Test
    fun classicError_withThrowable_emitsErrorEvent() = runTest {
        val logger = SimpleLogger("test.classic.error", Level.TRACE)
        val ex = RuntimeException("kaboom")

        logger.error(ex) { "operation failed" }

        val event = appender.awaitEvent { it.logger == "test.classic.error" }
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.message).isEqualTo("operation failed")
        assertThat(event.throwable).isSameInstanceAs(ex)
    }

    @Test
    fun classicLazyMessage_notEvaluatedWhenLevelDisabled() = runTest {
        val logger = SimpleLogger("test.lazy.off", Level.INFO)
        var evaluated = false

        logger.debug { evaluated = true; "msg" }

        assertThat(evaluated).isFalse()
        // Prove no DEBUG event was emitted: a marker INFO is the first event we see from this logger.
        logger.info("marker")
        val first = appender.awaitEvent { it.logger == "test.lazy.off" }
        assertThat(first.message).isEqualTo("marker")
    }

    @Test
    fun classicLazyMessage_evaluatedWhenLevelEnabled() = runTest {
        val logger = SimpleLogger("test.lazy.on", Level.DEBUG)
        var evaluated = false

        logger.debug { evaluated = true; "msg" }

        assertThat(evaluated).isTrue()
        val event = appender.awaitEvent { it.logger == "test.lazy.on" }
        assertThat(event.level).isEqualTo(Level.DEBUG)
        assertThat(event.message).isEqualTo("msg")
    }

    // --- logged(...) : the @Logged runtime helper ----------------------------------------------

    @Test
    fun logged_normalCompletion_emitsEntryThenExitAndReturnsResult() = runTest {
        val logger = SimpleLogger("test.logged.ok", Level.TRACE)

        val result = logger.logged(Level.INFO, null, "compute", "1, 2") { 42 }

        assertThat(result).isEqualTo(42)
        val events = appender.awaitEvents(2) { it.logger == "test.logged.ok" }
        assertThat(events[0].level).isEqualTo(Level.INFO)
        assertThat(events[0].message).isEqualTo("→ compute(1, 2)")
        assertThat(events[1].level).isEqualTo(Level.INFO)
        assertThat(events[1].message).startsWith("← compute = 42 (")
        assertThat(events[1].throwable).isNull()
    }

    @Test
    fun logged_exception_emitsEntryThenErrorAndRethrows() = runTest {
        val logger = SimpleLogger("test.logged.err", Level.TRACE)
        val boom = IllegalStateException("boom")

        val thrown = assertFailsWith<IllegalStateException> {
            logger.logged<Unit>(Level.INFO, null, "compute", "") { throw boom }
        }

        assertThat(thrown).isSameInstanceAs(boom)
        val events = appender.awaitEvents(2) { it.logger == "test.logged.err" }
        assertThat(events[0].level).isEqualTo(Level.INFO)
        assertThat(events[0].message).isEqualTo("→ compute()")
        assertThat(events[1].level).isEqualTo(Level.ERROR)
        assertThat(events[1].message).startsWith("✗ compute failed (")
        assertThat(events[1].throwable).isSameInstanceAs(boom)
    }

    @Test
    fun logged_propagatesSpanToEmittedLines() = runTest {
        val logger = SimpleLogger("test.logged.span", Level.TRACE)
        val span = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")

        logger.logged(Level.INFO, span, "compute", "") { }

        val events = appender.awaitEvents(2) { it.logger == "test.logged.span" }
        assertThat(events[0].span).isSameInstanceAs(span)
        assertThat(events[1].span).isSameInstanceAs(span)
    }

    // --- Companion factory / registry ----------------------------------------------------------

    @Test
    fun of_byName_returnsSimpleLogger_andCachesByName() {
        val a = Logger.of("test.factory.ByName")
        val b = Logger.of("test.factory.ByName")
        assertThat(a).isInstanceOf(SimpleLogger::class)
        assertThat(a.name).isEqualTo("test.factory.ByName")
        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun of_byClass_cachesInstance() {
        val a = Logger.of(SampleForFactory::class)
        val b = Logger.of(SampleForFactory::class)
        assertThat(a).isSameInstanceAs(b)
        assertThat(a.name).isNotEmpty()
    }

    // --- Collector mute/unmute -----------------------------------------------------------------

    @Test
    fun mute_gatesLogging_andUnmuteRestores() = runTest {
        val logger = SimpleLogger("test.mute", Level.INFO)

        logger.info("before")
        val before = appender.awaitEvent { it.logger == "test.mute" }
        assertThat(before.message).isEqualTo("before")

        logger.mute()
        assertThat(logger.isMuted()).isTrue()
        logger.info("while muted") // suppressed

        logger.unmute()
        assertThat(logger.isMuted()).isFalse()
        logger.info("after")

        // If muting had not gated the middle log, "while muted" would be the next event, not "after".
        val next = appender.awaitEvent { it.logger == "test.mute" }
        assertThat(next.message).isEqualTo("after")
    }

    // --- extended: classic API breadth (trace / warn / args) -----------------------------------

    @Test
    fun classicTraceAndWarn_emitAtTheirLevels() = runTest {
        val logger = SimpleLogger("test.classic.levels", Level.TRACE)

        logger.trace("t-msg")
        logger.warn("w-msg")

        val events = appender.awaitEvents(2) { it.logger == "test.classic.levels" }
        assertThat(events[0].level).isEqualTo(Level.TRACE)
        assertThat(events[0].message).isEqualTo("t-msg")
        assertThat(events[1].level).isEqualTo(Level.WARN)
        assertThat(events[1].message).isEqualTo("w-msg")
    }

    @Test
    fun classicTrace_withArguments_carriesThemVerbatim() = runTest {
        val logger = SimpleLogger("test.classic.trace.args", Level.TRACE)

        logger.trace("value = {}", 99)

        val event = appender.awaitEvent { it.logger == "test.classic.trace.args" }
        assertThat(event.message).isEqualTo("value = {}")
        assertThat(event.arguments.toList()).containsExactly(99)
    }

    @Test
    fun classicWarn_lazyWithThrowable_capturesBoth() = runTest {
        val logger = SimpleLogger("test.classic.warn.lazy", Level.TRACE)
        val ex = RuntimeException("warned")

        logger.warn(ex) { "lazy warn" }

        val event = appender.awaitEvent { it.logger == "test.classic.warn.lazy" }
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.message).isEqualTo("lazy warn")
        assertThat(event.throwable).isSameInstanceAs(ex)
    }

    // --- extended: span-scoped classic methods -------------------------------------------------

    @Test
    fun spanScopedInfo_attachesSpanToEvent() = runTest {
        val logger = SimpleLogger("test.span.info", Level.TRACE)
        val span = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")

        logger.info(span, "with span")

        val event = appender.awaitEvent { it.logger == "test.span.info" }
        assertThat(event.level).isEqualTo(Level.INFO)
        assertThat(event.message).isEqualTo("with span")
        assertThat(event.span).isSameInstanceAs(span)
    }

    @Test
    fun spanScopedError_withThrowable_attachesSpanAndThrowable() = runTest {
        val logger = SimpleLogger("test.span.error", Level.TRACE)
        val span = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")
        val ex = RuntimeException("span-boom")

        logger.error(span, "failed", ex)

        val event = appender.awaitEvent { it.logger == "test.span.error" }
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.span).isSameInstanceAs(span)
        assertThat(event.throwable).isSameInstanceAs(ex)
    }

    @Test
    fun spanScopedLazyDebug_notEvaluatedWhenLevelDisabled() = runTest {
        val logger = SimpleLogger("test.span.lazy", Level.INFO)
        val span = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")
        var evaluated = false

        logger.debug(span) { evaluated = true; "x" }

        assertThat(evaluated).isFalse()
        logger.info(span, "marker")
        val first = appender.awaitEvent { it.logger == "test.span.lazy" }
        assertThat(first.message).isEqualTo("marker")
        assertThat(first.span).isSameInstanceAs(span)
    }

    // --- extended: logged(...) interaction with the logger's own level -------------------------

    @Test
    fun logged_whenEntryExitLevelBelowThreshold_stillLogsErrorOnThrow() = runTest {
        val logger = SimpleLogger("test.logged.gated.err", Level.WARN)
        val boom = IllegalStateException("boom")

        assertFailsWith<IllegalStateException> {
            logger.logged<Unit>(Level.INFO, null, "op", "") { throw boom }
        }

        // The entry/exit lines are at INFO (< WARN, suppressed); the failure line is at ERROR (emitted).
        val event = appender.awaitEvent { it.logger == "test.logged.gated.err" }
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.message).startsWith("✗ op failed (")
        assertThat(event.throwable).isSameInstanceAs(boom)
    }

    @Test
    fun logged_whenEntryExitLevelBelowThreshold_normalCompletionEmitsNothing() = runTest {
        val logger = SimpleLogger("test.logged.gated.ok", Level.WARN)

        val result = logger.logged(Level.INFO, null, "op", "") { 7 }

        assertThat(result).isEqualTo(7)
        // Both entry and exit are at INFO (< WARN), so nothing is emitted; a WARN marker comes first.
        logger.warn("marker")
        val first = appender.awaitEvent { it.logger == "test.logged.gated.ok" }
        assertThat(first.message).isEqualTo("marker")
    }

    // --- extended: dynamic level & registry mute -----------------------------------------------

    @Test
    fun changingLevelAtRuntime_gatesSubsequentLogs() = runTest {
        val logger = SimpleLogger("test.dynamic.level", Level.INFO)

        logger.level = Level.ERROR
        logger.info("suppressed") // INFO < ERROR now
        logger.error("emitted")

        val first = appender.awaitEvent { it.logger == "test.dynamic.level" }
        assertThat(first.level).isEqualTo(Level.ERROR)
        assertThat(first.message).isEqualTo("emitted")
    }

    @Test
    fun registryMute_gatesRegisteredLogger_andUnmuteRestores() = runTest {
        val name = "test.registry.mute"
        val logger = SimpleLogger(name, Level.INFO)
        Logger.registry.register(logger)

        Logger.registry.mute(name)
        assertThat(logger.isMuted()).isTrue()
        logger.info("while muted") // suppressed

        Logger.registry.unmute(name)
        assertThat(logger.isMuted()).isFalse()
        logger.info("after")

        val next = appender.awaitEvent { it.logger == name }
        assertThat(next.message).isEqualTo("after")
    }

    @Test
    fun loggerConstructedAtOff_reportsMuted() {
        val logger = SimpleLogger("test.off.muted", Level.OFF)
        assertThat(logger.isMuted()).isTrue()
    }
}
