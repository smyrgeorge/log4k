package io.github.smyrgeorge.log4k.impl.appenders.simple

import assertk.assertThat
import assertk.assertions.containsExactly
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.TracingEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests for the `install()` companions of the simple appenders: each must register its appender
 * with the matching [RootLogger] registry and — by default — unregister every other appender
 * there. The global registries are process-wide, so they are saved before and restored after
 * every test.
 */
class SimpleAppenderInstallTests {

    private var savedLogging: List<Appender<LoggingEvent>> = emptyList()
    private var savedTracing: List<Appender<TracingEvent>> = emptyList()
    private var savedMetering: List<Appender<MeteringEvent>> = emptyList()

    @BeforeTest
    fun setup() {
        savedLogging = RootLogger.Logging.appenders.all()
        savedTracing = RootLogger.Tracing.appenders.all()
        savedMetering = RootLogger.Metering.appenders.all()
    }

    @AfterTest
    fun teardown() {
        RootLogger.Logging.appenders.unregisterAll()
        savedLogging.forEach { RootLogger.Logging.appenders.register(it) }
        RootLogger.Tracing.appenders.unregisterAll()
        savedTracing.forEach { RootLogger.Tracing.appenders.register(it) }
        RootLogger.Metering.appenders.unregisterAll()
        savedMetering.forEach { RootLogger.Metering.appenders.register(it) }
    }

    @Test
    fun consoleLogging_install_becomesTheOnlyLoggingAppender() {
        val installed = SimpleConsoleLoggingAppender.install()
        assertThat(RootLogger.Logging.appenders.all()).containsExactly(installed)
    }

    @Test
    fun jsonConsoleLogging_install_becomesTheOnlyLoggingAppender() {
        val installed = SimpleJsonConsoleLoggingAppender.install()
        assertThat(RootLogger.Logging.appenders.all()).containsExactly(installed)
    }

    @Test
    fun consoleTracing_install_becomesTheOnlyTracingAppender() {
        val installed = SimpleConsoleTracingAppender.install()
        assertThat(RootLogger.Tracing.appenders.all()).containsExactly(installed)
    }

    @Test
    fun consoleMetering_install_becomesTheOnlyMeteringAppender() {
        val installed = SimpleConsoleMeteringAppender.install()
        assertThat(RootLogger.Metering.appenders.all()).containsExactly(installed)
    }

    @Test
    fun meteringCollector_install_becomesTheOnlyMeteringAppender() {
        val installed = SimpleMeteringCollectorAppender.install()
        assertThat(RootLogger.Metering.appenders.all()).containsExactly(installed)
    }

    @Test
    fun install_keepingOthers_registersAlongsideTheExistingAppenders() {
        val first = SimpleConsoleLoggingAppender.install()
        val second = SimpleJsonConsoleLoggingAppender.install(unregisterOthers = false)
        assertThat(RootLogger.Logging.appenders.all()).containsExactly(first, second)
    }
}
