package io.github.smyrgeorge.log4k.slf4j.appender

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.messageContains
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.impl.Tags
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender
import io.github.smyrgeorge.log4k.slf4j.Log4kILoggerFactory
import io.github.smyrgeorge.log4k.slf4j.appender.utils.CapturingSlf4jLoggerFactory
import io.github.smyrgeorge.log4k.slf4j.appender.utils.Slf4jCapture
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.slf4j.event.Level as Slf4jLevel

/**
 * Integration tests for [Slf4jLoggingAppender]. Each test registers the appender, drives a real log4k
 * [Logger], and asserts on the call the capturing SLF4J backend received at the far end of the pipeline:
 * `Logger.log` (a level gate included) -> `RootLogger` queue -> [Slf4jLoggingAppender] -> `LoggerFactory` ->
 * backend logger.
 *
 * Delivery is asynchronous, so the tests run inside [runTest] and suspend on `Slf4jCapture.await(...)`
 * until the call in question has been forwarded. Suppression ("no call") cases are proven
 * deterministic by ordering against a marker log rather than with a timeout.
 *
 * Every test uses its own logger name because both the log4k logger registry and the SLF4J logger
 * factory are process-wide.
 */
class Slf4jLoggingAppenderTests {

    private lateinit var appender: Slf4jLoggingAppender

    // RootLogger registers a default console appender. Detach whatever is there, install only the
    // appender under test (which also keeps the build output clean), and restore afterward.
    private var saved: List<Appender<LoggingEvent>> = emptyList()

    @BeforeTest
    fun setup() {
        saved = RootLogger.Logging.appenders.all()
        RootLogger.Logging.appenders.unregisterAll()
        appender = Slf4jLoggingAppender()
        RootLogger.Logging.appenders.register(appender)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Logging.appenders.unregisterAll()
        saved.forEach { RootLogger.Logging.appenders.register(it) }
    }

    /** Resolves a log4k logger and forces its level, so tests are independent of the global default. */
    private fun logger(name: String, level: Level = Level.TRACE): Logger =
        Logger.of(name).also { Logger.registry.setLevel(name, level) }

    private fun Logger.emit(
        level: Level,
        message: String,
        arguments: Array<out Any?> = emptyArray(),
        throwable: Throwable? = null,
        span: TracingEvent.Span? = null,
        tags: Tags = emptyMap(),
    ) = log(level, span, tags, message, arguments, throwable)

    // --- Wiring --------------------------------------------------------------------------------

    @Test
    fun slf4j_isBoundToTheCapturingBackend() {
        // If SLF4J silently fell back to its NOP provider, every other test would prove nothing.
        assertThat(LoggerFactory.getILoggerFactory()).isSameInstanceAs(CapturingSlf4jLoggerFactory)
    }

    @Test
    fun call_isRoutedToTheSlf4jLoggerOfTheSameName() = runTest {
        logger("appender.routing").emit(Level.INFO, "m")

        val captured = Slf4jCapture.await { it.logger == "appender.routing" }
        assertThat(captured.logger).isEqualTo("appender.routing")
    }

    // --- Level mapping -------------------------------------------------------------------------

    @Test
    fun trace_mapsToSlf4jTrace() = runTest {
        logger("appender.map.trace").emit(Level.TRACE, "m")
        assertThat(Slf4jCapture.await { it.logger == "appender.map.trace" }.level).isEqualTo(Slf4jLevel.TRACE)
    }

    @Test
    fun debug_mapsToSlf4jDebug() = runTest {
        logger("appender.map.debug").emit(Level.DEBUG, "m")
        assertThat(Slf4jCapture.await { it.logger == "appender.map.debug" }.level).isEqualTo(Slf4jLevel.DEBUG)
    }

    @Test
    fun info_mapsToSlf4jInfo() = runTest {
        logger("appender.map.info").emit(Level.INFO, "m")
        assertThat(Slf4jCapture.await { it.logger == "appender.map.info" }.level).isEqualTo(Slf4jLevel.INFO)
    }

    @Test
    fun warn_mapsToSlf4jWarn() = runTest {
        logger("appender.map.warn").emit(Level.WARN, "m")
        assertThat(Slf4jCapture.await { it.logger == "appender.map.warn" }.level).isEqualTo(Slf4jLevel.WARN)
    }

    @Test
    fun error_mapsToSlf4jError() = runTest {
        logger("appender.map.error").emit(Level.ERROR, "m")
        assertThat(Slf4jCapture.await { it.logger == "appender.map.error" }.level).isEqualTo(Slf4jLevel.ERROR)
    }

    @Test
    fun off_isDroppedByTheAppender() = runTest {
        // OFF passes log4k's level gate (it outranks every threshold) and reaches the appender as an
        // event; there is no SLF4J counterpart, so the appender must swallow it.
        val log = logger("appender.map.off")

        log.emit(Level.OFF, "suppressed")
        log.emit(Level.ERROR, "marker")

        // Had OFF been forwarded, it would have been the first call from this logger.
        val first = Slf4jCapture.await { it.logger == "appender.map.off" }
        assertThat(first.message).isEqualTo("marker")
    }

    // --- Message and arguments -----------------------------------------------------------------

    @Test
    fun message_keepsPlaceholdersAndArgumentsVerbatim() = runTest {
        logger("appender.args").emit(Level.INFO, "user {} logged in from {}", arrayOf("alice", "127.0.0.1"))

        // Substitution is the backend's concern; the appender must hand over the raw pattern plus args,
        // so structured encoders keep the individual arguments.
        val captured = Slf4jCapture.await { it.logger == "appender.args" }
        assertThat(captured.message).isEqualTo("user {} logged in from {}")
        assertThat(captured.arguments).containsExactly("alice", "127.0.0.1")
    }

    @Test
    fun messageWithoutArguments_carriesNoArgumentsAndNoKeyValues() = runTest {
        logger("appender.args.none").emit(Level.INFO, "plain message")

        val captured = Slf4jCapture.await { it.logger == "appender.args.none" }
        assertThat(captured.message).isEqualTo("plain message")
        assertThat(captured.arguments).isEmpty()
        assertThat(captured.keyValues).isEmpty()
        assertThat(captured.throwable).isNull()
    }

    @Test
    fun nullArgument_isForwardedAsIs() = runTest {
        logger("appender.args.null").emit(Level.INFO, "value: {}", arrayOf(null))

        val captured = Slf4jCapture.await { it.logger == "appender.args.null" }
        assertThat(captured.arguments).containsExactly(null)
    }

    // --- Throwable -----------------------------------------------------------------------------

    @Test
    fun throwable_becomesTheSlf4jCause() = runTest {
        val boom = IllegalStateException("boom")

        logger("appender.throwable").emit(Level.ERROR, "failed for {}", arrayOf(7), boom)

        val captured = Slf4jCapture.await { it.logger == "appender.throwable" }
        assertThat(captured.throwable).isSameInstanceAs(boom)
        assertThat(captured.message).isEqualTo("failed for {}")
        assertThat(captured.arguments).containsExactly(7)
    }

    // --- Tags ----------------------------------------------------------------------------------

    @Test
    fun tags_becomeKeyValuePairs() = runTest {
        logger("appender.tags").log(
            level = Level.INFO,
            span = null,
            message = "m",
            arguments = emptyArray(),
            throwable = null,
            tags = mapOf("tenant" to "acme", "attempt" to 2),
        )

        val captured = Slf4jCapture.await { it.logger == "appender.tags" }
        assertThat(captured.keyValues).containsExactly(
            "tenant" to "acme",
            "attempt" to 2,
        )
    }

    // --- Span correlation ------------------------------------------------------------------------

    @Test
    fun spanCorrelatedEvent_carriesTraceIdAndSpanIdKeyValues() = runTest {
        val span = TracingEvent.Span.Local(
            id = "span-1",
            name = "test-span",
            level = Level.INFO,
            tracer = Tracer.of("appender.span.tracer"),
        )

        logger("appender.span").emit(Level.INFO, "m", span = span)

        val captured = Slf4jCapture.await { it.logger == "appender.span" }
        assertThat(captured.keyValues).containsExactly(
            "traceId" to span.context.traceId,
            "spanId" to "span-1",
        )
    }

    // --- Level gating ----------------------------------------------------------------------------

    @Test
    fun belowLog4kThreshold_neverReachesTheBackend() = runTest {
        val log = logger("appender.gate.log4k", Level.WARN)

        log.emit(Level.INFO, "suppressed")
        log.emit(Level.WARN, "marker")

        val first = Slf4jCapture.await { it.logger == "appender.gate.log4k" }
        assertThat(first.message).isEqualTo("marker")
    }

    @Test
    fun backendDisabledLevel_isDroppedBySlf4j() = runTest {
        // log4k lets everything through (TRACE); the backend is configured at WARN, so the NOP-builder
        // path of `atLevel(...)` must swallow the INFO call.
        CapturingSlf4jLoggerFactory.getLogger("appender.gate.backend").level = Slf4jLevel.WARN
        val log = logger("appender.gate.backend")

        log.emit(Level.INFO, "suppressed")
        log.emit(Level.WARN, "marker")

        val first = Slf4jCapture.await { it.logger == "appender.gate.backend" }
        assertThat(first.message).isEqualTo("marker")
    }

    // --- install() -------------------------------------------------------------------------------

    @Test
    fun install_replacesTheDefaultConsoleAppenderAndKeepsTheRest() {
        val console = SimpleConsoleLoggingAppender()
        RootLogger.Logging.appenders.register(console)

        val installed = Slf4jLoggingAppender.install()

        val names = RootLogger.Logging.appenders.all().map { it.name }
        assertThat(names).contains(installed.name)
        assertThat(names).doesNotContain(console.name)
        // The appender registered by setup() plays the role of one the application added deliberately.
        assertThat(RootLogger.Logging.appenders.all()).contains(appender)
    }

    // --- Loop guard ------------------------------------------------------------------------------

    @Test
    fun guard_rejectsAnSlf4jBackedByLog4k() {
        assertFailure { Slf4jLoggingAppender.checkNotBackedByLog4k(Log4kILoggerFactory()) }
            .isInstanceOf(IllegalStateException::class)
            .messageContains("log4k-slf4j")
    }

    @Test
    fun guard_acceptsAnyOtherBackend() {
        Slf4jLoggingAppender.checkNotBackedByLog4k(CapturingSlf4jLoggerFactory)
    }
}
