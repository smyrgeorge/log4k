package io.github.smyrgeorge.log4k.annotation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.startsWith
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.utils.CapturingLoggingAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

// --- Fixtures instrumented by the log4k-compiler-plugin (wired onto the test compilations) --------

@Logged
private class LoggedFixture {
    fun compute(x: Int): Int = x * x // INFO entry/exit "→ LoggedFixture.compute(x=..)"

    @Logged(level = Level.DEBUG)
    fun quiet(x: Int): Int = x + 1 // per-function level override

    @NoLog
    fun silent(x: Int): Int = x - 1 // opted out

    fun boom(): Nothing = error("kaboom") // exception path -> ERROR line + rethrow
}

@NoLog
private class SilencedLoggedFixture {
    @Logged
    fun ignored(): Int = 0 // class-level @NoLog kill switch -> not logged despite @Logged
}

/**
 * End-to-end tests for the [Logged] / [NoLog] annotations. The compiler plugin is applied to the test
 * compilations, so the fixtures above are really instrumented; each test drives a fixture and asserts
 * on the log lines that flowed through the `RootLogger -> Channel -> appender` pipeline. Assertions
 * match on the instrumentation name (`ClassName.functionName`), which is platform-independent.
 */
class LoggedTests {

    private lateinit var appender: CapturingLoggingAppender
    private var saved: List<Appender<LoggingEvent>> = emptyList()
    private var savedLevel: Level = Level.INFO

    @BeforeTest
    fun setup() {
        savedLevel = RootLogger.Logging.level
        RootLogger.Logging.level = Level.TRACE // ensure the synthesized `_log_` never gates a line
        saved = RootLogger.Logging.appenders.all()
        RootLogger.Logging.appenders.unregisterAll()
        appender = CapturingLoggingAppender()
        RootLogger.Logging.appenders.register(appender)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Logging.appenders.unregisterAll()
        saved.forEach { RootLogger.Logging.appenders.register(it) }
        RootLogger.Logging.level = savedLevel
    }

    @Test
    fun classLevelLogged_emitsEntryAndExit() = runTest {
        val result = LoggedFixture().compute(5)

        assertThat(result).isEqualTo(25)
        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.compute") }
        assertThat(events[0].level).isEqualTo(Level.INFO)
        assertThat(events[0].message).isEqualTo("→ LoggedFixture.compute(x=5)")
        assertThat(events[1].level).isEqualTo(Level.INFO)
        assertThat(events[1].message).startsWith("← LoggedFixture.compute = 25 (")
    }

    @Test
    fun logged_perFunctionLevelOverride_usesThatLevel() = runTest {
        LoggedFixture().quiet(1)

        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.quiet") }
        assertThat(events[0].level).isEqualTo(Level.DEBUG)
        assertThat(events[0].message).isEqualTo("→ LoggedFixture.quiet(x=1)")
    }

    @Test
    fun noLog_onFunction_optsOut() = runTest {
        val fixture = LoggedFixture()
        fixture.silent(1)   // @NoLog -> no log
        fixture.compute(2)  // marker

        val first = appender.awaitEvent {
            it.message.contains("LoggedFixture.silent") || it.message.contains("LoggedFixture.compute")
        }
        assertThat(first.message).isEqualTo("→ LoggedFixture.compute(x=2)")
    }

    @Test
    fun noLog_onClass_disablesEverything() = runTest {
        SilencedLoggedFixture().ignored() // class @NoLog -> nothing, even though method is @Logged
        LoggedFixture().compute(3)        // marker

        val first = appender.awaitEvent {
            it.message.contains("SilencedLoggedFixture.ignored") || it.message.contains("LoggedFixture.compute")
        }
        assertThat(first.message).isEqualTo("→ LoggedFixture.compute(x=3)")
    }

    @Test
    fun logged_exceptionPath_logsErrorLineAndRethrows() = runTest {
        val thrown = assertFailsWith<IllegalStateException> { LoggedFixture().boom() }

        assertThat(thrown.message).isEqualTo("kaboom")
        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.boom") }
        assertThat(events[0].message).isEqualTo("→ LoggedFixture.boom()")
        assertThat(events[1].level).isEqualTo(Level.ERROR)
        assertThat(events[1].message).startsWith("✗ LoggedFixture.boom failed (")
        assertThat(events[1].throwable).isSameInstanceAs(thrown)
    }
}
