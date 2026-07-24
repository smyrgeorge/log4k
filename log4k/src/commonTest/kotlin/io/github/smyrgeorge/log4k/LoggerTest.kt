package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
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
import io.github.smyrgeorge.log4k.impl.SimpleLogger
import io.github.smyrgeorge.log4k.utils.CapturingLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [Logger], driven through [CapturingLogger] so every assertion is synchronous and
 * isolated from the asynchronous `RootLogger` pipeline.
 */
class LoggerTest {

    private class SampleForFactory

    // Logger.log(...) forwards each built event to the global RootLogger, which would print through
    // the default console appender. Capturing doesn't need that path, so we detach the logging
    // appenders for the test and restore them afterwards, keeping output clean and not leaking state
    // into other test classes.
    private var savedAppenders: List<Appender<LoggingEvent>> = emptyList()

    @BeforeTest
    fun silenceRootLogger() {
        savedAppenders = RootLogger.Logging.appenders.all()
        RootLogger.Logging.appenders.unregisterAll()
    }

    @AfterTest
    fun restoreRootLogger() {
        RootLogger.Logging.appenders.unregisterAll()
        savedAppenders.forEach { RootLogger.Logging.appenders.register(it) }
    }

    // --- Level gating (log() + enabled()) ------------------------------------------------------

    @Test
    fun log_belowThreshold_buildsNoEvent() {
        val logger = CapturingLogger(level = Level.INFO)
        logger.log(Level.DEBUG, null, "debug msg", emptyArray(), null)
        assertThat(logger.events).isEmpty()
    }

    @Test
    fun log_atThreshold_buildsEvent() {
        val logger = CapturingLogger(level = Level.INFO)
        logger.log(Level.INFO, null, "info msg", emptyArray(), null)
        assertThat(logger.events).hasSize(1)
        assertThat(logger.last.level).isEqualTo(Level.INFO)
        assertThat(logger.last.message).isEqualTo("info msg")
    }

    @Test
    fun log_aboveThreshold_buildsEvent() {
        val logger = CapturingLogger(level = Level.INFO)
        logger.log(Level.ERROR, null, "boom", emptyArray(), null)
        assertThat(logger.events).hasSize(1)
        assertThat(logger.last.level).isEqualTo(Level.ERROR)
    }

    @Test
    fun log_whenLevelOff_buildsNothingEvenForError() {
        val logger = CapturingLogger(level = Level.OFF)
        logger.log(Level.ERROR, null, "boom", emptyArray(), null)
        assertThat(logger.events).isEmpty()
    }

    @Test
    fun isEnabled_reflectsConfiguredThreshold() {
        val logger = CapturingLogger(level = Level.WARN)
        assertThat(logger.isEnabled(Level.TRACE)).isFalse()
        assertThat(logger.isEnabled(Level.DEBUG)).isFalse()
        assertThat(logger.isEnabled(Level.INFO)).isFalse()
        assertThat(logger.isEnabled(Level.WARN)).isTrue()
        assertThat(logger.isEnabled(Level.ERROR)).isTrue()
    }

    // --- Event construction (delegation to toLoggingEvent) -------------------------------------

    @Test
    fun log_propagatesAllFieldsToLoggingEvent() {
        val logger = CapturingLogger(name = "my.logger", level = Level.TRACE)
        val ex = RuntimeException("x")
        val span = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")

        logger.log(Level.WARN, span, "msg {}", arrayOf<Any?>("a", 1), ex)

        val e = logger.last
        assertThat(e.logger).isEqualTo("my.logger")
        assertThat(e.level).isEqualTo(Level.WARN)
        assertThat(e.message).isEqualTo("msg {}")
        assertThat(e.arguments.toList()).containsExactly("a", 1)
        assertThat(e.throwable).isSameInstanceAs(ex)
        assertThat(e.span).isSameInstanceAs(span)
    }

    @Test
    fun log_withoutSpanOrThrowable_leavesThemNull() {
        val logger = CapturingLogger(level = Level.TRACE)
        logger.log(Level.INFO, null, "m", emptyArray(), null)
        assertThat(logger.last.span).isNull()
        assertThat(logger.last.throwable).isNull()
    }

    // --- Classic extension API integration -----------------------------------------------------

    @Test
    fun classicInfo_capturesSingleInfoEvent() {
        val logger = CapturingLogger(level = Level.TRACE)
        logger.info("hello world")
        assertThat(logger.events).hasSize(1)
        assertThat(logger.last.level).isEqualTo(Level.INFO)
        assertThat(logger.last.message).isEqualTo("hello world")
        assertThat(logger.last.throwable).isNull()
        assertThat(logger.last.arguments.toList()).isEmpty()
    }

    @Test
    fun classicMessage_keepsPlaceholdersAndArgumentsVerbatim() {
        val logger = CapturingLogger(level = Level.TRACE)
        logger.info("user {} logged in from {}", "alice", "127.0.0.1")
        assertThat(logger.events).hasSize(1)
        // log4k does not interpolate placeholders at the Logger layer; the raw message and the
        // arguments are carried through untouched for a downstream appender to render.
        assertThat(logger.last.message).isEqualTo("user {} logged in from {}")
        assertThat(logger.last.arguments.toList()).containsExactly("alice", "127.0.0.1")
    }

    @Test
    fun classicError_withThrowable_capturesErrorEvent() {
        val logger = CapturingLogger(level = Level.TRACE)
        val ex = RuntimeException("kaboom")
        logger.error(ex) { "operation failed" }
        assertThat(logger.events).hasSize(1)
        assertThat(logger.last.level).isEqualTo(Level.ERROR)
        assertThat(logger.last.message).isEqualTo("operation failed")
        assertThat(logger.last.throwable).isSameInstanceAs(ex)
    }

    @Test
    fun classicLazyMessage_notEvaluatedWhenLevelDisabled() {
        val logger = CapturingLogger(level = Level.INFO)
        var evaluated = false
        logger.debug { evaluated = true; "msg" }
        assertThat(evaluated).isFalse()
        assertThat(logger.events).isEmpty()
    }

    @Test
    fun classicLazyMessage_evaluatedWhenLevelEnabled() {
        val logger = CapturingLogger(level = Level.DEBUG)
        var evaluated = false
        logger.debug { evaluated = true; "msg" }
        assertThat(evaluated).isTrue()
        assertThat(logger.events).hasSize(1)
        assertThat(logger.last.level).isEqualTo(Level.DEBUG)
        assertThat(logger.last.message).isEqualTo("msg")
    }

    // --- logged(...) : the @Logged runtime helper ----------------------------------------------

    @Test
    fun logged_normalCompletion_emitsEntryThenExitAndReturnsResult() {
        val logger = CapturingLogger(level = Level.TRACE)

        val result = logger.logged(Level.INFO, null, "compute", "1, 2") { 42 }

        assertThat(result).isEqualTo(42)
        assertThat(logger.events).hasSize(2)

        val entry = logger.events[0]
        assertThat(entry.level).isEqualTo(Level.INFO)
        assertThat(entry.message).isEqualTo("→ compute(1, 2)")

        val exit = logger.events[1]
        assertThat(exit.level).isEqualTo(Level.INFO)
        assertThat(exit.message).startsWith("← compute = 42 (")
        assertThat(exit.throwable).isNull()
    }

    @Test
    fun logged_exception_emitsEntryThenErrorAndRethrows() {
        val logger = CapturingLogger(level = Level.TRACE)
        val boom = IllegalStateException("boom")

        val thrown = assertFailsWith<IllegalStateException> {
            logger.logged<Unit>(Level.INFO, null, "compute", "") { throw boom }
        }

        assertThat(thrown).isSameInstanceAs(boom)
        assertThat(logger.events).hasSize(2)

        val entry = logger.events[0]
        assertThat(entry.level).isEqualTo(Level.INFO)
        assertThat(entry.message).isEqualTo("→ compute()")

        val error = logger.events[1]
        assertThat(error.level).isEqualTo(Level.ERROR)
        assertThat(error.message).startsWith("✗ compute failed (")
        assertThat(error.throwable).isSameInstanceAs(boom)
    }

    @Test
    fun logged_propagatesSpanToEmittedLines() {
        val logger = CapturingLogger(level = Level.TRACE)
        val span = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")

        logger.logged(Level.INFO, span, "compute", "") { }

        assertThat(logger.events).hasSize(2)
        assertThat(logger.events[0].span).isSameInstanceAs(span)
        assertThat(logger.events[1].span).isSameInstanceAs(span)
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
    fun mute_gatesLogging_andUnmuteRestores() {
        val logger = CapturingLogger(level = Level.INFO)

        logger.info("before")
        assertThat(logger.events).hasSize(1)

        logger.mute()
        assertThat(logger.isMuted()).isTrue()
        logger.info("while muted")
        assertThat(logger.events).hasSize(1)

        logger.unmute()
        assertThat(logger.isMuted()).isFalse()
        logger.info("after")
        assertThat(logger.events).hasSize(2)
    }
}
