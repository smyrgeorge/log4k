package io.github.smyrgeorge.log4k.slf4j

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.slf4j.utils.CapturingLoggingAppender
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import io.github.smyrgeorge.log4k.Logger as CoreLogger
import org.slf4j.Logger as Slf4jLogger

/**
 * Integration tests for [Log4kLogger]. Each test registers a [CapturingLoggingAppender], drives a real
 * `org.slf4j.Logger` obtained from `LoggerFactory`, and asserts on the [LoggingEvent] that came out the
 * far end of the pipeline: SLF4J call site -> `AbstractLogger` normalization -> [Log4kLogger] ->
 * `Logger.log` (level gate included) -> `RootLogger` queue -> appender.
 *
 * Delivery is asynchronous, so the tests run inside [runTest] and suspend on `awaitEvent(...)` until the
 * event in question has been appended. Suppression ("no event") cases are proven deterministically by
 * ordering against a marker log rather than with a timeout.
 *
 * Every test uses its own logger name, because the log4k logger registry is process-wide and shared with
 * the core API.
 */
class Log4kLoggerTests {

    private lateinit var appender: CapturingLoggingAppender

    // RootLogger registers a default console appender. Detach whatever is there, install only our
    // capturing appender for the test (which also keeps the build output clean), and restore afterwards.
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

    /** Resolves an SLF4J logger and forces its log4k level, so tests are independent of the global default. */
    private fun logger(name: String, level: Level = Level.TRACE): Slf4jLogger =
        LoggerFactory.getLogger(name).also { CoreLogger.registry.setLevel(name, level) }

    // --- Level mapping -------------------------------------------------------------------------

    @Test
    fun trace_mapsToLevelTrace() = runTest {
        logger("slf4j.map.trace").trace("m")
        assertThat(appender.awaitEvent { it.logger == "slf4j.map.trace" }.level).isEqualTo(Level.TRACE)
    }

    @Test
    fun debug_mapsToLevelDebug() = runTest {
        logger("slf4j.map.debug").debug("m")
        assertThat(appender.awaitEvent { it.logger == "slf4j.map.debug" }.level).isEqualTo(Level.DEBUG)
    }

    @Test
    fun info_mapsToLevelInfo() = runTest {
        logger("slf4j.map.info").info("m")
        assertThat(appender.awaitEvent { it.logger == "slf4j.map.info" }.level).isEqualTo(Level.INFO)
    }

    @Test
    fun warn_mapsToLevelWarn() = runTest {
        logger("slf4j.map.warn").warn("m")
        assertThat(appender.awaitEvent { it.logger == "slf4j.map.warn" }.level).isEqualTo(Level.WARN)
    }

    @Test
    fun error_mapsToLevelError() = runTest {
        logger("slf4j.map.error").error("m")
        assertThat(appender.awaitEvent { it.logger == "slf4j.map.error" }.level).isEqualTo(Level.ERROR)
    }

    // --- Level gating --------------------------------------------------------------------------

    @Test
    fun isEnabled_reflectsConfiguredThreshold() {
        val log = logger("slf4j.enabled", Level.WARN)

        assertThat(log.isTraceEnabled).isFalse()
        assertThat(log.isDebugEnabled).isFalse()
        assertThat(log.isInfoEnabled).isFalse()
        assertThat(log.isWarnEnabled).isTrue()
        assertThat(log.isErrorEnabled).isTrue()
    }

    @Test
    fun isEnabled_whenMuted_isFalseForEveryLevel() {
        val log = logger("slf4j.enabled.muted")
        CoreLogger.registry.mute("slf4j.enabled.muted")
        try {
            assertThat(log.isTraceEnabled).isFalse()
            assertThat(log.isErrorEnabled).isFalse()
        } finally {
            CoreLogger.registry.unmute("slf4j.enabled.muted")
        }
    }

    @Test
    fun belowThreshold_emitsNoEvent() = runTest {
        val log = logger("slf4j.gate.below", Level.WARN)

        log.info("suppressed")
        log.warn("marker")

        // Had INFO not been gated, it would have been the first event from this logger.
        val first = appender.awaitEvent { it.logger == "slf4j.gate.below" }
        assertThat(first.level).isEqualTo(Level.WARN)
        assertThat(first.message).isEqualTo("marker")
    }

    @Test
    fun whenMuted_emitsNothingEvenForError() = runTest {
        val muted = logger("slf4j.gate.muted")
        val marker = logger("slf4j.gate.muted.marker")
        CoreLogger.registry.mute("slf4j.gate.muted")
        try {
            muted.error("suppressed")
            marker.info("marker")

            val first = appender.awaitEvent {
                it.logger == "slf4j.gate.muted" || it.logger == "slf4j.gate.muted.marker"
            }
            assertThat(first.logger).isEqualTo("slf4j.gate.muted.marker")
        } finally {
            CoreLogger.registry.unmute("slf4j.gate.muted")
        }
    }

    // --- Message and arguments -----------------------------------------------------------------

    @Test
    fun name_isTheLoggerName() {
        assertThat(logger("slf4j.name").name).isEqualTo("slf4j.name")
    }

    @Test
    fun message_keepsPlaceholdersAndArgumentsVerbatim() = runTest {
        logger("slf4j.args").info("user {} logged in from {}", "alice", "127.0.0.1")

        // Substitution is an appender concern; the bridge must hand over the raw pattern plus args.
        val event = appender.awaitEvent { it.logger == "slf4j.args" }
        assertThat(event.message).isEqualTo("user {} logged in from {}")
        assertThat(event.arguments.toList()).containsExactly("alice", "127.0.0.1")
    }

    @Test
    fun messageWithoutArguments_carriesEmptyArguments() = runTest {
        logger("slf4j.args.none").info("plain message")

        val event = appender.awaitEvent { it.logger == "slf4j.args.none" }
        assertThat(event.message).isEqualTo("plain message")
        assertThat(event.arguments.toList()).isEmpty()
        assertThat(event.throwable).isNull()
    }

    @Test
    fun nullMessage_becomesTheStringNull() = runTest {
        logger("slf4j.null").info(null as String?)

        val event = appender.awaitEvent { it.logger == "slf4j.null" }
        assertThat(event.message).isEqualTo("null")
        assertThat(event.arguments.toList()).isEmpty()
    }

    // --- Throwable normalization ---------------------------------------------------------------
    // Log4kLogger overrides none of the per-level methods, so SLF4J's own normalization decides what
    // counts as the exception. These tests pin that behavior down; before it was inherited, a trailing
    // Throwable was consumed as an ordinary formatting argument and its stack trace was lost.

    @Test
    fun explicitThrowableOverload_attachesThrowable() = runTest {
        val boom = IllegalStateException("boom")

        logger("slf4j.throwable.explicit").error("failed", boom)

        val event = appender.awaitEvent { it.logger == "slf4j.throwable.explicit" }
        assertThat(event.throwable).isSameInstanceAs(boom)
        assertThat(event.message).isEqualTo("failed")
        assertThat(event.arguments.toList()).isEmpty()
    }

    @Test
    fun trailingThrowableAfterOneArgument_isExtractedAndRemovedFromArguments() = runTest {
        val boom = IllegalStateException("boom")

        logger("slf4j.throwable.trailing").error("failed for {}", 7, boom)

        val event = appender.awaitEvent { it.logger == "slf4j.throwable.trailing" }
        assertThat(event.throwable).isSameInstanceAs(boom)
        assertThat(event.message).isEqualTo("failed for {}")
        assertThat(event.arguments.toList()).containsExactly(7)
    }

    @Test
    fun trailingThrowableAfterVarargs_isExtractedAndRemovedFromArguments() = runTest {
        val boom = IllegalStateException("boom")

        logger("slf4j.throwable.varargs").error("{} {} failed", 1, 2, boom)

        val event = appender.awaitEvent { it.logger == "slf4j.throwable.varargs" }
        assertThat(event.throwable).isSameInstanceAs(boom)
        assertThat(event.message).isEqualTo("{} {} failed")
        assertThat(event.arguments.toList()).containsExactly(1, 2)
    }

    @Test
    fun throwableThatIsNotLast_staysAFormattingArgument() = runTest {
        val boom = IllegalStateException("boom")

        logger("slf4j.throwable.middle").error("{} then {}", boom, "x")

        val event = appender.awaitEvent { it.logger == "slf4j.throwable.middle" }
        assertThat(event.throwable).isNull()
        assertThat(event.arguments.toList()).containsExactly(boom, "x")
    }

    @Test
    fun singleThrowableArgumentTypedAsAny_staysAFormattingArgument() = runTest {
        val boom = IllegalStateException("boom")

        // Forcing the (String, Object) overload: SLF4J only extracts a *trailing* throwable when the
        // call carries more than one argument, so here it stays an ordinary argument. Note that plain
        // `error("failed {}", boom)` binds to the more specific (String, Throwable) overload instead.
        logger("slf4j.throwable.single").error("failed {}", boom as Any)

        val event = appender.awaitEvent { it.logger == "slf4j.throwable.single" }
        assertThat(event.throwable).isNull()
        assertThat(event.arguments.toList()).containsExactly(boom)
    }

    @Test
    fun noArgumentsAndNoThrowable_leavesThrowableNull() = runTest {
        logger("slf4j.throwable.absent").warn("nothing to see")

        val event = appender.awaitEvent { it.logger == "slf4j.throwable.absent" }
        assertThat(event.throwable).isNull()
    }

    // --- Fluent API ----------------------------------------------------------------------------

    @Test
    fun fluentSetCause_attachesThrowable() = runTest {
        val boom = IllegalStateException("boom")

        logger("slf4j.fluent.cause").atError().setCause(boom).log("fluent {}", "msg")

        val event = appender.awaitEvent { it.logger == "slf4j.fluent.cause" }
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.throwable).isSameInstanceAs(boom)
        assertThat(event.arguments.toList()).containsExactly("msg")
    }

    @Test
    fun fluentApi_respectsLevelGating() = runTest {
        val log = logger("slf4j.fluent.gated", Level.WARN)

        log.atInfo().log("suppressed")
        log.atWarn().log("marker")

        val first = appender.awaitEvent { it.logger == "slf4j.fluent.gated" }
        assertThat(first.level).isEqualTo(Level.WARN)
    }

    // --- Event metadata ------------------------------------------------------------------------

    @Test
    fun event_carriesLoggerNameThreadAndNoSpan() = runTest {
        logger("slf4j.metadata").info("m")

        val event = appender.awaitEvent { it.logger == "slf4j.metadata" }
        assertThat(event.logger).isEqualTo("slf4j.metadata")
        assertThat(event.thread).isEqualTo(Thread.currentThread().name)
        // The bridge has no tracing context to draw on, so events are never span-correlated.
        assertThat(event.span).isNull()
    }
}
