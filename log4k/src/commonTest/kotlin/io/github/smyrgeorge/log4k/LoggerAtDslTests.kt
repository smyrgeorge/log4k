package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.impl.SimpleLogger
import io.github.smyrgeorge.log4k.impl.extensions.atDebug
import io.github.smyrgeorge.log4k.impl.extensions.atError
import io.github.smyrgeorge.log4k.impl.extensions.atInfo
import io.github.smyrgeorge.log4k.impl.extensions.atTrace
import io.github.smyrgeorge.log4k.impl.extensions.atWarn
import io.github.smyrgeorge.log4k.utils.CapturingLoggingAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Integration tests for the builder-style logging DSL ([Logger.at] and the [atTrace] .. [atError]
 * extension shorthands). Like [LoggerTests], each test registers a [CapturingLoggingAppender], drives a
 * real [SimpleLogger], and asserts on what actually flowed through the
 * `RootLogger -> Channel -> appender` pipeline — proving the builder's properties are handed to
 * [Logger.log] correctly (tags -> tags, cause -> throwable, etc.).
 *
 * Delivery is asynchronous, so the tests run inside [runTest] and suspend on `awaitEvent(...)` /
 * `awaitEvents(...)`. Suppression ("no event") cases are proven deterministically by ordering
 * against a marker log rather than with a timeout.
 */
class LoggerAtDslTests {

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

    // --- Event construction --------------------------------------------------------------------

    @Test
    fun atWarn_buildsTheEventFromTheDslBlock() = runTest {
        val logger = SimpleLogger("test.at.warn", Level.TRACE)
        val bar = "bar"
        val exception = RuntimeException("boom")

        logger.atWarn {
            message = "foo $bar"
            cause = exception
            tags = buildMap(capacity = 3) {
                put("foo", 1)
                put("bar", "x")
                put("obj", Pair(2, 3))
            }
        }

        val event = appender.awaitEvent { it.logger == "test.at.warn" }
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.message).isEqualTo("foo bar")
        assertThat(event.throwable).isSameInstanceAs(exception)
        // The tags become the event's tags, values carried verbatim (Int, String, Pair, ...).
        assertThat(event.tags).isEqualTo(mapOf("foo" to 1, "bar" to "x", "obj" to Pair(2, 3)))
        assertThat(event.span).isNull()
        assertThat(event.arguments.toList()).isEmpty()
    }

    @Test
    fun at_propagatesEveryBuilderPropertyToTheEvent() = runTest {
        val logger = SimpleLogger("test.at.full", Level.TRACE)
        val ex = RuntimeException("x")
        val theSpan = Tracer.of("t").span(id = "sid", traceId = "tid", name = "s")

        // Every parameter of log(level, span, tags, message, arguments, throwable) is reachable.
        logger.at(Level.WARN) {
            message = "msg {} {}"
            cause = ex
            tags = mapOf("tenant" to "acme")
            span = theSpan
            arguments = arrayOf<Any?>("a", 1)
        }

        val event = appender.awaitEvent { it.logger == "test.at.full" }
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.message).isEqualTo("msg {} {}")
        assertThat(event.arguments.toList()).containsExactly("a", 1)
        assertThat(event.throwable).isSameInstanceAs(ex)
        assertThat(event.span).isSameInstanceAs(theSpan)
        assertThat(event.tags).isEqualTo(mapOf<String, Any>("tenant" to "acme"))
    }

    @Test
    fun at_withDefaults_emitsEmptyMessageAndNoContext() = runTest {
        val logger = SimpleLogger("test.at.defaults", Level.TRACE)

        logger.atInfo { }

        val event = appender.awaitEvent { it.logger == "test.at.defaults" }
        assertThat(event.level).isEqualTo(Level.INFO)
        assertThat(event.message).isEqualTo("")
        assertThat(event.tags).isEqualTo(emptyMap())
        assertThat(event.throwable).isNull()
        assertThat(event.span).isNull()
        assertThat(event.arguments.toList()).isEmpty()
    }

    @Test
    fun at_messageKeepsPlaceholdersAndArgumentsVerbatim() = runTest {
        val logger = SimpleLogger("test.at.args", Level.TRACE)

        logger.atInfo {
            message = "user {} logged in from {}"
            arguments = arrayOf("alice", "127.0.0.1")
        }

        // log4k does not interpolate placeholders at the Logger layer; the raw message and the
        // arguments are carried through untouched for a downstream appender to render.
        val event = appender.awaitEvent { it.logger == "test.at.args" }
        assertThat(event.message).isEqualTo("user {} logged in from {}")
        assertThat(event.arguments.toList()).containsExactly("alice", "127.0.0.1")
    }

    @Test
    fun eachInvocation_getsAFreshBuilder() = runTest {
        val logger = SimpleLogger("test.at.fresh", Level.TRACE)
        val ex = RuntimeException("first")

        logger.atWarn {
            message = "first"
            cause = ex
            tags = mapOf("k" to "v")
        }
        logger.atWarn {
            message = "second"
        }

        // The second event must not inherit any state from the first invocation's builder.
        val events = appender.awaitEvents(2) { it.logger == "test.at.fresh" }
        assertThat(events[0].message).isEqualTo("first")
        assertThat(events[0].throwable).isSameInstanceAs(ex)
        assertThat(events[0].tags).isEqualTo(mapOf<String, Any>("k" to "v"))
        assertThat(events[1].message).isEqualTo("second")
        assertThat(events[1].throwable).isNull()
        assertThat(events[1].tags).isEqualTo(emptyMap())
    }

    // --- Level dispatch --------------------------------------------------------------------------

    @Test
    fun atLevelShorthands_emitAtTheirRespectiveLevels() = runTest {
        val logger = SimpleLogger("test.at.levels", Level.TRACE)

        logger.atTrace { message = "t" }
        logger.atDebug { message = "d" }
        logger.atInfo { message = "i" }
        logger.atWarn { message = "w" }
        logger.atError { message = "e" }

        val events = appender.awaitEvents(5) { it.logger == "test.at.levels" }
        assertThat(events.map { it.level }).containsExactly(
            Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR,
        )
        assertThat(events.map { it.message }).containsExactly("t", "d", "i", "w", "e")
    }

    @Test
    fun at_withExplicitLevel_matchesTheShorthand() = runTest {
        val logger = SimpleLogger("test.at.explicit", Level.TRACE)

        logger.at(Level.ERROR) { message = "via at()" }

        val event = appender.awaitEvent { it.logger == "test.at.explicit" }
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.message).isEqualTo("via at()")
    }

    // --- Level gating & laziness -----------------------------------------------------------------

    @Test
    fun at_belowThreshold_blockIsNeverExecuted() = runTest {
        val logger = SimpleLogger("test.at.lazy.off", Level.INFO)
        var evaluated = false

        logger.atDebug {
            evaluated = true
            message = "never built"
        }

        assertThat(evaluated).isFalse()
        // Prove no DEBUG event was emitted: a marker INFO is the first event we see from this logger.
        logger.atInfo { message = "marker" }
        val first = appender.awaitEvent { it.logger == "test.at.lazy.off" }
        assertThat(first.message).isEqualTo("marker")
    }

    @Test
    fun at_atOrAboveThreshold_blockIsExecuted() = runTest {
        val logger = SimpleLogger("test.at.lazy.on", Level.DEBUG)
        var evaluated = false

        logger.atDebug {
            evaluated = true
            message = "built"
        }

        assertThat(evaluated).isTrue()
        val event = appender.awaitEvent { it.logger == "test.at.lazy.on" }
        assertThat(event.level).isEqualTo(Level.DEBUG)
        assertThat(event.message).isEqualTo("built")
    }

    @Test
    fun at_levelOff_isAlwaysDiscardedWithoutRunningTheBlock() = runTest {
        val logger = SimpleLogger("test.at.off.filter", Level.TRACE)
        var evaluated = false

        // OFF is a filter level, not an event level: it must never pass the gate.
        logger.at(Level.OFF) {
            evaluated = true
            message = "should never appear"
        }

        assertThat(evaluated).isFalse()
        logger.atInfo { message = "marker" }
        val first = appender.awaitEvent { it.logger == "test.at.off.filter" }
        assertThat(first.message).isEqualTo("marker")
    }

    @Test
    fun at_onMutedLogger_skipsBlock_andUnmuteRestores() = runTest {
        val logger = SimpleLogger("test.at.mute", Level.INFO)
        var evaluated = false

        logger.mute()
        logger.atError {
            evaluated = true
            message = "while muted"
        }
        assertThat(evaluated).isFalse()

        logger.unmute()
        logger.atError { message = "after" }

        // If muting had not gated the call, "while muted" would be the next event, not "after".
        val first = appender.awaitEvent { it.logger == "test.at.mute" }
        assertThat(first.message).isEqualTo("after")
    }

    @Test
    fun changingLevelAtRuntime_gatesSubsequentAtCalls() = runTest {
        val logger = SimpleLogger("test.at.dynamic", Level.INFO)

        logger.level = Level.ERROR
        logger.atWarn { message = "suppressed" } // WARN < ERROR now
        logger.atError { message = "emitted" }

        val first = appender.awaitEvent { it.logger == "test.at.dynamic" }
        assertThat(first.level).isEqualTo(Level.ERROR)
        assertThat(first.message).isEqualTo("emitted")
    }

    // --- Failure inside the block ----------------------------------------------------------------

    @Test
    fun aThrowingBlock_propagates_andEmitsNothing() = runTest {
        val logger = SimpleLogger("test.at.throwing", Level.TRACE)
        val boom = IllegalStateException("builder boom")

        val thrown = assertFailsWith<IllegalStateException> {
            logger.atWarn {
                message = "partially built"
                throw boom
            }
        }

        assertThat(thrown).isSameInstanceAs(boom)
        // The half-configured event must not have been logged; a marker is the first event we see.
        logger.atInfo { message = "marker" }
        val first = appender.awaitEvent { it.logger == "test.at.throwing" }
        assertThat(first.message).isEqualTo("marker")
    }
}
