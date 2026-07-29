package io.github.smyrgeorge.log4k.annotation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.startsWith
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.utils.CapturingLoggingAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// --- Fixtures instrumented by the log4k-compiler-plugin (wired onto the test compilations) --------

@Logged
private class LoggedFixture {
    fun compute(x: Int): Int = x * x // INFO entry/exit "→ LoggedFixture.compute(x=..)"

    @Logged(level = Level.DEBUG)
    fun quiet(x: Int): Int = x + 1 // per-function level override

    @Logged(tags = [Tag("component", "billing")])
    fun tagged(x: Int): Int = x + 2 // entry/exit lines carry tags = {component=billing}

    @NoLog
    fun silent(x: Int): Int = x - 1 // opted out

    fun boom(): Nothing = error("kaboom") // exception path -> ERROR line + rethrow

    fun login(username: String, @Masked password: String): Boolean = password.isNotEmpty() // masked param

    fun rotate(@Masked oldSecret: String, @Masked newSecret: String): Int = oldSecret.length + newSecret.length

    fun handle(@Masked secret: Any, visible: Int): Int = visible + 1 // the masked value is never rendered
}

@Logged(tags = [Tag("layer", "service")])
private class TaggedLoggedFixture {
    fun plain(): Int = 1 // class-level tags apply

    @Logged(tags = [Tag("layer", "billing")])
    fun overridden(): Int = 2 // a function tag with the same key wins over the class-level one
}

@NoLog
private class SilencedLoggedFixture {
    @Logged
    fun ignored(): Int = 0 // class-level @NoLog kill switch -> not logged despite @Logged
}

private class RenamedLoggerFixture {
    // Not named `log`: reused anyway, as the class's single `Logger`-typed property.
    @Suppress("unused") // used by the generated @Logged code (added after the frontend).
    private val myLogger = Logger.of("custom.renamed.logger")

    @Logged
    fun work(x: Int): Int = x + 1
}

private class AmbiguousLoggerFixture {
    // Two `Logger` properties and none named `log`: ambiguous -> the plugin synthesizes `_log_`.
    @Suppress("unused")
    private val first = Logger.of("custom.ambiguous.first")

    @Suppress("unused")
    private val second = Logger.of("custom.ambiguous.second")

    @Logged
    fun work(x: Int): Int = x + 2
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
    fun logged_linesCarryTheDeclarationCallSite() = runTest {
        // `compute` is instrumented by the class-level @Logged: the call site must still point at
        // the function's own declaration in this file, and be identical on the entry and exit lines.
        LoggedFixture().compute(3)

        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.compute") }
        val site = assertNotNull(events[0].callSite)
        assertThat(site.file).isEqualTo("LoggedTests.kt")
        assertThat(site.function).isEqualTo("LoggedFixture.compute")
        assertThat(site.line).isGreaterThan(0)
        assertThat(events[1].callSite).isEqualTo(site)
    }

    @Test
    fun logged_failureLineCarriesTheDeclarationCallSite() = runTest {
        assertFailsWith<IllegalStateException> { LoggedFixture().boom() }

        val event = appender.awaitEvent { it.message.startsWith("✗ LoggedFixture.boom") }
        val site = assertNotNull(event.callSite)
        assertThat(site.file).isEqualTo("LoggedTests.kt")
        assertThat(site.function).isEqualTo("LoggedFixture.boom")
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
    fun logged_tags_areAttachedToEveryEmittedLine() = runTest {
        LoggedFixture().tagged(1)

        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.tagged") }
        assertThat(events[0].tags).isEqualTo(mapOf<String, Any>("component" to "billing"))
        assertThat(events[1].tags).isEqualTo(mapOf<String, Any>("component" to "billing"))
    }

    @Test
    fun logged_untagged_carriesEmptyTags() = runTest {
        LoggedFixture().compute(4)

        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.compute") }
        assertThat(events[0].tags).isEqualTo(emptyMap<String, Any>())
    }

    @Test
    fun logged_classLevelTags_applyAndFunctionTagsWin() = runTest {
        val fixture = TaggedLoggedFixture()

        fixture.plain()
        val plain = appender.awaitEvents(2) { it.message.contains("TaggedLoggedFixture.plain") }
        assertThat(plain[0].tags).isEqualTo(mapOf<String, Any>("layer" to "service"))

        fixture.overridden()
        val overridden = appender.awaitEvents(2) { it.message.contains("TaggedLoggedFixture.overridden") }
        assertThat(overridden[0].tags).isEqualTo(mapOf<String, Any>("layer" to "billing"))
    }

    @Test
    fun masked_parameter_rendersPlaceholderInsteadOfTheValue() = runTest {
        val result = LoggedFixture().login("alice", "hunter2")

        assertThat(result).isEqualTo(true)
        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.login") }
        // The unmasked parameter renders normally; the @Masked one renders as the placeholder.
        assertThat(events[0].message).isEqualTo("→ LoggedFixture.login(username=alice, password=<MASKED>)")
        // Masking is parameter-only: the exit line still renders the real result.
        assertThat(events[1].message).startsWith("← LoggedFixture.login = true (")
    }

    @Test
    fun masked_onEveryParameter_masksThemAll() = runTest {
        LoggedFixture().rotate("old-secret", "new-secret")

        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.rotate") }
        assertThat(events[0].message).isEqualTo("→ LoggedFixture.rotate(oldSecret=<MASKED>, newSecret=<MASKED>)")
    }

    @Test
    fun masked_parameterValue_isNeverToStringed() = runTest {
        var renders = 0
        val secret = object {
            override fun toString(): String {
                renders++
                return "leaked"
            }
        }

        LoggedFixture().handle(secret, 1)

        // The value must not be rendered at all — not even built and discarded.
        assertThat(renders).isEqualTo(0)
        val events = appender.awaitEvents(2) { it.message.contains("LoggedFixture.handle") }
        assertThat(events[0].message).isEqualTo("→ LoggedFixture.handle(secret=<MASKED>, visible=1)")
    }

    @Test
    fun logged_loggerPropertyUnderAnotherName_isReusedByType() = runTest {
        RenamedLoggerFixture().work(1)

        val events = appender.awaitEvents(2) { it.message.contains("RenamedLoggerFixture.work") }
        // The class's single `Logger`-typed property is reused, so the lines carry its custom name.
        assertThat(events[0].logger).isEqualTo("custom.renamed.logger")
        assertThat(events[1].logger).isEqualTo("custom.renamed.logger")
    }

    @Test
    fun logged_ambiguousLoggerProperties_useASynthesizedLogger() = runTest {
        AmbiguousLoggerFixture().work(1)

        val events = appender.awaitEvents(2) { it.message.contains("AmbiguousLoggerFixture.work") }
        // Two candidates are never guessed between: a `_log_` logger is synthesized instead.
        assertThat(events[0].logger).isNotEqualTo("custom.ambiguous.first")
        assertThat(events[0].logger).isNotEqualTo("custom.ambiguous.second")
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
