package io.github.smyrgeorge.log4k.impl.appenders.simple

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleJsonConsoleLoggingAppender.Companion.formatJson
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleJsonConsoleLoggingAppender.Companion.printJson
import io.github.smyrgeorge.log4k.utils.loggingEvent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/**
 * Tests for [SimpleJsonConsoleLoggingAppender]. Rather than only substring-matching, each test
 * parses the produced string back with kotlinx-serialization — proving the output is well-formed
 * JSON (correctly escaped) and asserting the fields structurally. Events come from [loggingEvent],
 * whose fixed timestamp/thread make expectations deterministic on every platform.
 */
class SimpleJsonConsoleLoggingAppenderTests {

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    // --- Field structure --------------------------------------------------------------------------

    @Test
    fun formatJson_rendersAllFields() {
        val obj = parse(loggingEvent().formatJson())
        assertThat(obj.getValue("id").jsonPrimitive.content).isEqualTo("1")
        assertThat(obj.getValue("id").jsonPrimitive.isString).isFalse() // a JSON number, not "1"
        assertThat(obj.getValue("level").jsonPrimitive.content).isEqualTo("INFO")
        assertThat(obj.getValue("timestamp").jsonPrimitive.content).isEqualTo("1970-01-01T00:00:00Z")
        assertThat(obj.getValue("logger").jsonPrimitive.content).isEqualTo("test.logger")
        assertThat(obj.getValue("message").jsonPrimitive.content).isEqualTo("hello world")
        assertThat(obj.getValue("thread").jsonPrimitive.content).isEqualTo("main")
    }

    @Test
    fun formatJson_omitsNullSpanAndThrowable() {
        val obj = parse(loggingEvent().formatJson())
        assertThat(obj.containsKey("span_id")).isFalse()
        assertThat(obj.containsKey("trace_id")).isFalse()
        assertThat(obj.containsKey("throwable")).isFalse()
    }

    @Test
    fun formatJson_omitsTheId_whenNotPositive() {
        val obj = parse(loggingEvent(id = 0).formatJson())
        assertThat(obj.containsKey("id")).isFalse()
    }

    @Test
    fun formatJson_includesSpanAndTraceIds_whenASpanIsAttached() {
        val span = Tracer.of("appenders.test.tracer").span(id = "span-9", traceId = "trace-9", name = "remote")
        val obj = parse(loggingEvent(span = span).formatJson())
        assertThat(obj.getValue("span_id").jsonPrimitive.content).isEqualTo("span-9")
        assertThat(obj.getValue("trace_id").jsonPrimitive.content).isEqualTo("trace-9")
    }

    @Test
    fun formatJson_usesCompactSerialization() {
        // Pin the on-the-wire shape (single line, no spaces) that log shippers rely on.
        val json = loggingEvent().formatJson()
        assertThat(json).contains("\"level\":\"INFO\"")
        assertThat(json.contains('\n')).isFalse()
    }

    // --- Message formatting and escaping ------------------------------------------------------------

    @Test
    fun formatJson_substitutesPlaceholders_acrossArgumentTypes() {
        val obj = parse(loggingEvent(message = "n={} b={} nil={}", arguments = arrayOf<Any?>(7, true, null)).formatJson())
        assertThat(obj.getValue("message").jsonPrimitive.content).isEqualTo("n=7 b=true nil=null")
    }

    @Test
    fun formatJson_escapesAndRoundTripsHostileStrings() {
        // Quotes, backslashes, newlines and tabs must be escaped so the output stays valid JSON —
        // proven by parsing it back and comparing the content to the original string.
        val nasty = "quote:\" backslash:\\ newline:\n tab:\t end"
        val obj = parse(loggingEvent(message = nasty, arguments = emptyArray()).formatJson())
        assertThat(obj.getValue("message").jsonPrimitive.content).isEqualTo(nasty)
    }

    @Test
    fun formatJson_escapesHostileLoggerAndThreadNames() {
        val obj = parse(loggingEvent(logger = "lo\"gg\ner", thread = "th\"read").formatJson())
        assertThat(obj.getValue("logger").jsonPrimitive.content).isEqualTo("lo\"gg\ner")
        assertThat(obj.getValue("thread").jsonPrimitive.content).isEqualTo("th\"read")
    }

    // --- Tags ----------------------------------------------------------------------------------------

    @Test
    fun formatJson_includesTagsAsAnObject() {
        val obj = parse(loggingEvent(tags = mapOf("tenant" to "acme", "attempt" to 2)).formatJson())
        val tags = obj.getValue("tags").jsonObject
        assertThat(tags.getValue("tenant").jsonPrimitive.content).isEqualTo("acme")
        assertThat(tags.getValue("attempt").jsonPrimitive.content).isEqualTo("2")
        assertThat(tags.getValue("attempt").jsonPrimitive.isString).isFalse() // numbers stay numbers
    }

    @Test
    fun formatJson_rendersEmptyTagsAsAnEmptyObject() {
        val obj = parse(loggingEvent().formatJson())
        assertThat(obj.getValue("tags").jsonObject.isEmpty()).isTrue()
    }

    @Test
    fun formatJson_survivesAThrowingTagValueToString() {
        // Rendering happens on the async appender coroutine, where a propagated exception would
        // silently drop the whole line: the bad tag value must only cost its own rendering.
        val bad = object {
            override fun toString(): String = error("toString boom")
        }
        val obj = parse(loggingEvent(tags = mapOf("bad" to bad, "good" to "v")).formatJson())
        val tags = obj.getValue("tags").jsonObject
        assertThat(tags.getValue("bad").jsonPrimitive.content).isEqualTo("<toString() failed>")
        assertThat(tags.getValue("good").jsonPrimitive.content).isEqualTo("v")
    }

    // --- Throwable rendering -------------------------------------------------------------------------

    @Test
    fun formatJson_includesTheStackTrace() {
        val obj = parse(loggingEvent(level = Level.ERROR, throwable = IllegalStateException("boom")).formatJson())
        val trace = obj.getValue("throwable").jsonPrimitive.content
        assertThat(trace).contains("IllegalStateException")
        assertThat(trace).contains("boom")
    }

    @Test
    fun formatJson_includesTheStackTrace_atAnyLevel() {
        val obj = parse(loggingEvent(level = Level.WARN, throwable = IllegalStateException("warn-boom")).formatJson())
        assertThat(obj.containsKey("throwable")).isTrue()
    }

    // --- Write path -----------------------------------------------------------------------------------

    @Test
    fun printJson_neverThrows() {
        loggingEvent().printJson()
        // The ERROR path exercises `platformPrintlnError` (the real error stream) on every platform.
        loggingEvent(level = Level.ERROR, throwable = IllegalStateException("boom")).printJson()
    }

    @Test
    fun append_neverThrows() = runTest {
        val appender = SimpleJsonConsoleLoggingAppender()
        appender.append(loggingEvent())
        appender.append(loggingEvent(level = Level.ERROR, throwable = IllegalStateException("boom")))
    }
}
