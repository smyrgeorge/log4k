package io.github.smyrgeorge.log4k.impl.appenders.simple

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender.Companion.format
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender.Companion.print
import io.github.smyrgeorge.log4k.utils.loggingEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for [SimpleConsoleLoggingAppender] — the default appender on most platforms. The
 * `internal` [format] function is asserted directly (with `colors = false` for exact-string
 * checks, and `colors = true` for the ANSI wrapping); [print]/`append` round-trips prove the
 * write path never throws. Events come from [loggingEvent], whose fixed timestamp/thread make
 * the expected lines deterministic on every platform.
 */
class SimpleConsoleLoggingAppenderTests {

    // --- Line structure -------------------------------------------------------------------------

    @Test
    fun format_rendersTheFullLine() {
        val line = loggingEvent().format(colors = false)
        assertThat(line).isEqualTo("1 1970-01-01T00:00:00Z [main] - INFO test.logger - hello world")
    }

    @Test
    fun format_prefixesTheSpanId_whenASpanIsAttached() {
        val span = Tracer.of("appenders.test.tracer").span(id = "span-1", traceId = "trace-1", name = "remote")
        val line = loggingEvent(span = span).format(colors = false)
        assertThat(line).isEqualTo("1 [span-1] 1970-01-01T00:00:00Z [main] - INFO test.logger - hello world")
    }

    @Test
    fun format_omitsTheId_whenNotPositive() {
        val line = loggingEvent(id = 0).format(colors = false)
        assertThat(line).isEqualTo("1970-01-01T00:00:00Z [main] - INFO test.logger - hello world")
    }

    @Test
    fun format_rendersEveryLevelName() {
        Level.entries.filter { it != Level.OFF }.forEach { level ->
            val line = loggingEvent(level = level).format(colors = false)
            assertThat(line).contains(" - ${level.name} test.logger - ")
        }
    }

    // --- Message formatting ----------------------------------------------------------------------

    @Test
    fun format_substitutesPlaceholders_acrossArgumentTypes() {
        val line = loggingEvent(message = "n={} b={} nil={}", arguments = arrayOf<Any?>(7, true, null)).format(colors = false)
        assertThat(line).contains(" - n=7 b=true nil=null")
    }

    @Test
    fun format_leavesExcessPlaceholdersLiteral_andIgnoresExcessArguments() {
        val excessPlaceholder = loggingEvent(message = "a {} {}", arguments = arrayOf("x")).format(colors = false)
        assertThat(excessPlaceholder).contains(" - a x {}")

        val excessArgument = loggingEvent(message = "a {}", arguments = arrayOf("x", "ignored")).format(colors = false)
        assertThat(excessArgument).contains(" - a x")
        assertThat(excessArgument).doesNotContain("ignored")
    }

    @Test
    fun format_respectsEscapedPlaceholders() {
        val line = loggingEvent(message = """count \{} = {}""", arguments = arrayOf(5)).format(colors = false)
        assertThat(line).contains(" - count {} = 5")
    }

    // --- Logger-name compaction -------------------------------------------------------------------

    @Test
    fun format_compactsLoggerNames_onlyBeyond36Chars() {
        val at36 = loggingEvent(logger = "io.github.smyrgeorge.log4k.Abcdefghi").format(colors = false)
        assertThat(at36).contains(" INFO io.github.smyrgeorge.log4k.Abcdefghi - ")

        val at37 = loggingEvent(logger = "io.github.smyrgeorge.log4k.Abcdefghij").format(colors = false)
        assertThat(at37).contains(" INFO i.g.s.log4k.Abcdefghij - ")
    }

    @Test
    fun compact_keepsTheLastTwoSegmentsIntact() {
        val line = loggingEvent(logger = "com.example.billing.checkout.OrderService").format(colors = false)
        assertThat(line).contains(" INFO c.e.b.checkout.OrderService - ")
    }

    @Test
    fun compact_toleratesEmptyLoggerNameSegments() {
        // A long name with an empty segment used to crash `compact()` (NoSuchElementException),
        // silently losing the event; empty segments must abbreviate to nothing instead.
        val doubleDot = loggingEvent(logger = "com..example.billing.checkout.OrderService").format(colors = false)
        assertThat(doubleDot).contains(" INFO c..e.b.checkout.OrderService - ")

        val leadingDot = loggingEvent(logger = ".com.example.billing.checkout.OrderService").format(colors = false)
        assertThat(leadingDot).contains(" INFO .c.e.b.checkout.OrderService - ")
    }

    @Test
    fun compact_leavesNamesWithFewerThanThreeSegmentsUntouched() {
        val noDots = "a".repeat(40)
        assertThat(loggingEvent(logger = noDots).format(colors = false)).contains(" INFO $noDots - ")

        val twoParts = "a".repeat(30) + ".ClassName"
        assertThat(loggingEvent(logger = twoParts).format(colors = false)).contains(" INFO $twoParts - ")
    }

    // --- Throwable rendering ----------------------------------------------------------------------

    @Test
    fun format_keepsTheStackTraceInTheSameString() {
        // The trace must be part of the same (single-write) string as its log line, never a
        // separate `printStackTrace()` call on another stream.
        val line = loggingEvent(level = Level.ERROR, throwable = IllegalStateException("boom")).format(colors = false)
        assertThat(line).contains(" - ERROR test.logger - hello world\n")
        assertThat(line).contains("IllegalStateException")
        assertThat(line).contains("boom")
        assertThat(line.endsWith("\n")).isFalse() // trailing whitespace is trimmed
    }

    @Test
    fun format_includesTheStackTrace_atAnyLevel() {
        val line = loggingEvent(level = Level.WARN, throwable = IllegalStateException("warn-boom")).format(colors = false)
        assertThat(line).contains("warn-boom")
    }

    // --- ANSI colors -------------------------------------------------------------------------------

    @Test
    fun format_withColors_wrapsTimestampLoggerAndSpan() {
        val span = Tracer.of("appenders.test.tracer").span(id = "span-2", traceId = "trace-2", name = "remote")
        val line = loggingEvent(span = span).format(colors = true)
        assertThat(line).contains("\u001B[35m[span-2] \u001B[0m")            // purple span prefix
        assertThat(line).contains("\u001B[32m1970-01-01T00:00:00Z\u001B[0m") // green timestamp
        assertThat(line).contains("\u001B[36mtest.logger\u001B[0m")          // cyan logger
    }

    @Test
    fun format_withColors_colorsTheLevel() {
        fun lineAt(level: Level) = loggingEvent(level = level).format(colors = true)
        assertThat(lineAt(Level.TRACE)).contains("\u001B[90mTRACE\u001B[0m") // grey
        assertThat(lineAt(Level.DEBUG)).contains("\u001B[90mDEBUG\u001B[0m") // grey
        assertThat(lineAt(Level.INFO)).contains("\u001B[34mINFO\u001B[0m")   // blue
        assertThat(lineAt(Level.WARN)).contains("\u001B[33mWARN\u001B[0m")   // yellow
        assertThat(lineAt(Level.ERROR)).contains("\u001B[31mERROR\u001B[0m") // red
    }

    @Test
    fun format_withoutColors_emitsNoEscapeCodes() {
        val span = Tracer.of("appenders.test.tracer").span(id = "span-3", traceId = "trace-3", name = "remote")
        val line = loggingEvent(span = span, level = Level.ERROR, throwable = IllegalStateException("x"))
            .format(colors = false)
        assertThat(line).doesNotContain("\u001B[")
    }

    // --- Write path --------------------------------------------------------------------------------

    @Test
    fun print_neverThrows() {
        loggingEvent().print()
        loggingEvent(level = Level.ERROR, throwable = IllegalStateException("boom")).print()
        loggingEvent(logger = "com..example.billing.checkout.OrderService").print()
    }

    @Test
    fun append_neverThrows() = runTest {
        val appender = SimpleConsoleLoggingAppender()
        appender.append(loggingEvent())
        appender.append(loggingEvent(level = Level.ERROR, throwable = IllegalStateException("boom")))
    }
}
