package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import io.github.smyrgeorge.log4k.classic.debug
import io.github.smyrgeorge.log4k.classic.error
import io.github.smyrgeorge.log4k.classic.info
import io.github.smyrgeorge.log4k.classic.warn
import io.github.smyrgeorge.log4k.impl.extensions.atInfo
import io.github.smyrgeorge.log4k.utils.CapturingLoggingAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * End-to-end tests for the compile-time call-site injection. The compiler plugin is applied to the
 * test compilations, so every log call in this file is really rewritten; each test drives the logger
 * and asserts on the [LoggingEvent.callSite] that flowed through the `RootLogger -> Channel ->
 * appender` pipeline. Assertions on [SourceLocation.line] stay coarse (`> 0`, relative ordering) so that
 * reformatting the file does not break them.
 */
class CallSiteTests {

    private val log = Logger.of("callsite.test.logger")
    private lateinit var appender: CapturingLoggingAppender
    private var saved: List<Appender<LoggingEvent>> = emptyList()
    private var savedRootLevel: Level = Level.INFO
    private var savedLoggerLevel: Level = Level.INFO

    @BeforeTest
    fun setup() {
        savedRootLevel = RootLogger.Logging.level
        RootLogger.Logging.level = Level.TRACE
        savedLoggerLevel = log.level
        log.level = Level.TRACE
        saved = RootLogger.Logging.appenders.all()
        RootLogger.Logging.appenders.unregisterAll()
        appender = CapturingLoggingAppender()
        RootLogger.Logging.appenders.register(appender)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Logging.appenders.unregisterAll()
        saved.forEach { RootLogger.Logging.appenders.register(it) }
        RootLogger.Logging.level = savedRootLevel
        log.level = savedLoggerLevel
    }

    // --- Logger.log -------------------------------------------------------------------------------

    @Test
    fun directLog_carriesCallSite() = runTest {
        log.log(Level.INFO, null, emptyMap(), "direct-log-message", emptyArray(), null)

        val event = appender.awaitEvent { it.message == "direct-log-message" }
        val site = assertNotNull(event.callSite)
        assertThat(site.file).isEqualTo("CallSiteTests.kt")
        assertThat(site.function).isEqualTo("CallSiteTests.directLog_carriesCallSite")
        assertThat(site.line).isGreaterThan(0)
    }

    // --- at DSL -----------------------------------------------------------------------------------

    @Test
    fun atDsl_carriesCallSite_andPreservesBuilderProperties() = runTest {
        val boom = IllegalStateException("at-boom")
        log.at(Level.ERROR) {
            message = "at-dsl-message"
            cause = boom
            tags = mapOf("at" to 1)
        }

        val event = appender.awaitEvent { it.message == "at-dsl-message" }
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.throwable).isSameInstanceAs(boom)
        assertThat(event.tags).isEqualTo(mapOf<String, Any>("at" to 1))
        val site = assertNotNull(event.callSite)
        assertThat(site.function).isEqualTo("CallSiteTests.atDsl_carriesCallSite_andPreservesBuilderProperties")
    }

    @Test
    fun atShorthand_carriesCallSite() = runTest {
        log.atInfo { message = "at-shorthand-message" }

        val event = appender.awaitEvent { it.message == "at-shorthand-message" }
        assertThat(event.level).isEqualTo(Level.INFO)
        val site = assertNotNull(event.callSite)
        assertThat(site.file).isEqualTo("CallSiteTests.kt")
        assertThat(site.function).isEqualTo("CallSiteTests.atShorthand_carriesCallSite")
    }

    // --- classic eager extensions -----------------------------------------------------------------

    @Test
    fun classicEager_carriesCallSite() = runTest {
        log.info("classic-eager {}", 42)

        val event = appender.awaitEvent { it.message == "classic-eager {}" }
        assertThat(event.level).isEqualTo(Level.INFO)
        assertThat(event.arguments.toList()).isEqualTo(listOf<Any?>(42))
        val site = assertNotNull(event.callSite)
        assertThat(site.file).isEqualTo("CallSiteTests.kt")
        assertThat(site.function).isEqualTo("CallSiteTests.classicEager_carriesCallSite")
    }

    @Test
    fun classicEager_mapsEveryParameter() = runTest {
        val span = Tracer.of("callsite.test.tracer").span(id = "span-cs", traceId = "trace-cs", name = "cs")
        val boom = IllegalStateException("cs-boom")
        log.warn(span, mapOf("k" to "v"), "classic-full {}", boom, 7)

        val event = appender.awaitEvent { it.message == "classic-full {}" }
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.span).isSameInstanceAs(span)
        assertThat(event.tags).isEqualTo(mapOf<String, Any>("k" to "v"))
        assertThat(event.throwable).isSameInstanceAs(boom)
        assertThat(event.arguments.toList()).isEqualTo(listOf<Any?>(7))
        assertNotNull(event.callSite)
    }

    @Test
    fun classicEager_preservesArgumentEvaluationOrder() = runTest {
        // `Logger.log`'s parameter order differs from the classic extension's (throwable moves after
        // the varargs) — the rewrite must still evaluate the argument expressions in source order.
        val order = mutableListOf<String>()
        fun <T> record(label: String, value: T): T {
            order.add(label)
            return value
        }

        log.error(record("msg", "order-check {}"), record("t", IllegalStateException("x")), record("arg", 1))

        appender.awaitEvent { it.message == "order-check {}" }
        assertThat(order).isEqualTo(listOf("msg", "t", "arg"))
    }

    @Test
    fun classicEager_supportsSpreadArguments() = runTest {
        log.info("spread {} {}", *arrayOf<Any?>(1, "two"))

        val event = appender.awaitEvent { it.message == "spread {} {}" }
        assertThat(event.arguments.toList()).isEqualTo(listOf<Any?>(1, "two"))
        assertNotNull(event.callSite)
    }

    // --- classic lazy extensions ------------------------------------------------------------------

    @Test
    fun classicLazy_carriesCallSite() = runTest {
        log.info { "classic-lazy-message" }

        val event = appender.awaitEvent { it.message == "classic-lazy-message" }
        val site = assertNotNull(event.callSite)
        assertThat(site.function).isEqualTo("CallSiteTests.classicLazy_carriesCallSite")
    }

    @Test
    fun classicLazy_mapsTheThrowable() = runTest {
        val boom = IllegalStateException("lazy-boom")
        log.warn(boom) { "classic-lazy-throwable" }

        val event = appender.awaitEvent { it.message == "classic-lazy-throwable" }
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.throwable).isSameInstanceAs(boom)
        assertNotNull(event.callSite)
    }

    @Test
    fun classicLazy_isNotEvaluated_whenTheLevelIsDisabled() = runTest {
        log.level = Level.WARN
        var evaluated = false
        log.debug {
            evaluated = true
            "lazy-disabled-message"
        }
        log.level = Level.TRACE

        assertThat(evaluated).isFalse()
        log.warn("lazy-disabled-marker") // prove the DEBUG line above never reached the pipeline.
        val event = appender.awaitEvent {
            it.message == "lazy-disabled-message" || it.message == "lazy-disabled-marker"
        }
        assertThat(event.message).isEqualTo("lazy-disabled-marker")
    }

    @Test
    fun classicLazy_withNonLocalReturn_isLeftUntouched() = runTest {
        // The lambda cannot be moved behind the level guard, so the plugin skips this call —
        // it must keep compiling and logging exactly as before, just without a call site.
        assertThat(logWithNonLocalReturn(early = true)).isEqualTo("early")
        assertThat(logWithNonLocalReturn(early = false)).isEqualTo("done")

        val event = appender.awaitEvent { it.message == "non-local-message" }
        assertThat(event.callSite).isNull()
    }

    private fun logWithNonLocalReturn(early: Boolean): String {
        log.info {
            if (early) return "early"
            "non-local-message"
        }
        return "done"
    }

    // --- attribution ------------------------------------------------------------------------------

    @Test
    fun callSite_insideLambda_isAttributedToTheEnclosingFunction() = runTest {
        listOf(1).forEach { log.info("inside-lambda-message") }

        val event = appender.awaitEvent { it.message == "inside-lambda-message" }
        val site = assertNotNull(event.callSite)
        assertThat(site.function).isEqualTo("CallSiteTests.callSite_insideLambda_isAttributedToTheEnclosingFunction")
    }

    @Test
    fun callSite_lineNumbers_followSourceOrder() = runTest {
        log.info("line-a")
        log.info("line-b")

        val a = appender.awaitEvent { it.message == "line-a" }
        val b = appender.awaitEvent { it.message == "line-b" }
        assertThat(assertNotNull(b.callSite).line).isGreaterThan(assertNotNull(a.callSite).line)
    }

    // --- SourceLocation itself --------------------------------------------------------------------------

    @Test
    fun callSite_rendersAsFunctionFileLine() {
        assertThat(SourceLocation(file = "Api.kt", line = 42, function = "Api.handle").toString())
            .isEqualTo("Api.handle(Api.kt:42)")
    }
}
