package io.github.smyrgeorge.log4k.integrations.otlp

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingEvent
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tests for [OtlpTracingAppender] against a fake OTLP endpoint backed by Ktor's [MockEngine],
 * so the whole suite is in-process (no sockets) and runs on every target. Spans are constructed
 * with fixed ids and timestamps, so the expected wire values are exact.
 */
class OtlpTracingAppenderTests {

    private class FakeEndpoint {
        data class Recorded(
            val method: String,
            val url: String,
            val contentType: String?,
            val headers: Map<String, String>,
            val body: String,
        )

        val requests = Channel<Recorded>(Channel.UNLIMITED)
        var status: HttpStatusCode = HttpStatusCode.OK

        val engine: MockEngine = MockEngine { request ->
            requests.trySend(
                Recorded(
                    method = request.method.value,
                    url = request.url.toString(),
                    contentType = request.body.contentType?.toString(),
                    headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") },
                    body = request.body.toByteArray().decodeToString(),
                )
            )
            respond(content = "", status = status)
        }
    }

    private val endpoint = FakeEndpoint()
    private val tracer = Tracer.of("otlp-test-tracer")

    private fun appender(
        size: Int = 2,
        timeout: Duration = 60.seconds,
        headers: Map<String, String> = emptyMap(),
    ): OtlpTracingAppender = OtlpTracingAppender(
        service = "svc",
        batchSize = size,
        batchTimeout = timeout,
        env = "test-env",
        version = "1.2.3",
        headers = headers,
        engine = endpoint.engine,
    )

    private fun span(
        name: String = "op",
        id: String = "00000000000000ff",
        traceId: String = "111111111111111100000000000000aa",
        parent: TracingEvent.Span? = null,
        tags: Map<String, Any> = emptyMap(),
        error: Throwable? = null,
        finished: Boolean = true,
    ): TracingEvent.Span.Local = TracingEvent.Span.Local(
        id = id,
        name = name,
        level = Level.INFO,
        tracer = tracer,
        parent = parent,
        tags = tags,
        traceId = traceId,
    ).apply {
        if (finished) {
            startAt = Instant.fromEpochSeconds(100, 250)
            endAt = Instant.fromEpochSeconds(101, 750)
        }
        // Mirror what `end()` does: OK by default, ERROR when a throwable is recorded.
        status = TracingEvent.Span.Status(
            code = if (error != null) TracingEvent.Span.Status.Code.ERROR else TracingEvent.Span.Status.Code.OK,
            error = error,
            description = error?.message,
        )
    }

    /** The spans of the request body: a flat list over resourceSpans/scopeSpans. */
    private fun spansOf(recorded: FakeEndpoint.Recorded): List<JsonObject> =
        Json.parseToJsonElement(recorded.body).jsonObject["resourceSpans"]!!.jsonArray.flatMap { rs ->
            rs.jsonObject["scopeSpans"]!!.jsonArray.flatMap { ss ->
                ss.jsonObject["spans"]!!.jsonArray.map { it.jsonObject }
            }
        }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    /** Typed OTLP attributes (`[{key, value: {xxxValue}}]`) as a map of key to value-object. */
    private fun JsonObject.attributes(): Map<String, JsonObject> =
        this["attributes"]?.jsonArray?.associate {
            it.jsonObject.str("key")!! to it.jsonObject["value"]!!.jsonObject
        } ?: emptyMap()

    @Test
    fun publishesSpans_inOtlpWireFormat() = runTest {
        val appender = appender(size = 2)
        val root = span(name = "root", id = "00000000000000ff")
        val child = span(name = "child", id = "0000000000000F0F", parent = root)
        appender.append(root)
        appender.append(child)

        val recorded = endpoint.requests.receive()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.url).isEqualTo("http://localhost:4318/v1/traces")
        assertThat(recorded.contentType!!).contains("application/json")

        val body = Json.parseToJsonElement(recorded.body).jsonObject
        val resourceSpans = body["resourceSpans"]!!.jsonArray
        assertThat(resourceSpans).hasSize(1)

        val resource = resourceSpans.single().jsonObject["resource"]!!.jsonObject.attributes()
        assertThat(resource["service.name"]!!.str("stringValue")).isEqualTo("svc")
        assertThat(resource["deployment.environment.name"]!!.str("stringValue")).isEqualTo("test-env")
        assertThat(resource["service.version"]!!.str("stringValue")).isEqualTo("1.2.3")

        val scope = resourceSpans.single().jsonObject["scopeSpans"]!!.jsonArray.single()
            .jsonObject["scope"]!!.jsonObject
        assertThat(scope.str("name")).isEqualTo("log4k")

        val spans = spansOf(recorded)
        assertThat(spans).hasSize(2)

        val rootJson = spans.single { it.str("name") == "root" }
        assertThat(rootJson.str("traceId")).isEqualTo("111111111111111100000000000000aa")
        assertThat(rootJson.str("spanId")).isEqualTo("00000000000000ff")
        assertThat(rootJson.str("parentSpanId")).isNull()
        assertThat(rootJson["kind"]!!.jsonPrimitive.int).isEqualTo(1) // SPAN_KIND_INTERNAL
        assertThat(rootJson.str("startTimeUnixNano")).isEqualTo("100000000250") // 100s + 250ns.
        assertThat(rootJson.str("endTimeUnixNano")).isEqualTo("101000000750") // 101s + 750ns.
        assertThat(rootJson["status"]!!.jsonObject["code"]!!.jsonPrimitive.int).isEqualTo(1) // OK

        val childJson = spans.single { it.str("name") == "child" }
        assertThat(childJson.str("parentSpanId")).isEqualTo("00000000000000ff")
        assertThat(childJson.str("spanId")).isEqualTo("0000000000000f0f") // Lowercased hex.
    }

    @Test
    fun tags_becomeTypedAttributes() = runTest {
        val appender = appender(size = 1)
        appender.append(
            span(tags = mapOf("retries" to 3, "ratio" to 0.5, "cached" to true, "user" to "alice"))
        )

        val attributes = spansOf(endpoint.requests.receive()).single().attributes()
        assertThat(attributes["retries"]!!.str("intValue")).isEqualTo("3") // int64 -> JSON string.
        assertThat(attributes["ratio"]!!["doubleValue"]!!.jsonPrimitive.content).isEqualTo("0.5")
        assertThat(attributes["cached"]!!["boolValue"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(attributes["user"]!!.str("stringValue")).isEqualTo("alice")
    }

    @Test
    fun nonFiniteDoubleTags_useTheProto3StringEncoding() = runTest {
        val appender = appender(size = 1)
        appender.append(
            span(
                tags = mapOf(
                    "nan" to Double.NaN,
                    "inf" to Double.POSITIVE_INFINITY,
                    "ninf" to Double.NEGATIVE_INFINITY,
                )
            )
        )

        // Parsing alone is significant: a bare NaN/Infinity literal would be invalid JSON.
        val attributes = spansOf(endpoint.requests.receive()).single().attributes()
        assertThat(attributes["nan"]!!.str("doubleValue")).isEqualTo("NaN")
        assertThat(attributes["inf"]!!.str("doubleValue")).isEqualTo("Infinity")
        assertThat(attributes["ninf"]!!.str("doubleValue")).isEqualTo("-Infinity")
    }

    @Test
    fun statusMessage_isOnlyEmittedForErrorStatuses() = runTest {
        val appender = appender(size = 1)
        val span = span()
        span.status = TracingEvent.Span.Status(
            code = TracingEvent.Span.Status.Code.OK,
            description = "all good",
        )
        appender.append(span)

        val status = spansOf(endpoint.requests.receive()).single()["status"]!!.jsonObject
        assertThat(status["code"]!!.jsonPrimitive.int).isEqualTo(1) // OK
        assertThat(status["message"]).isNull()
    }

    @Test
    fun spanEvents_mapToOtlpEvents() = runTest {
        val appender = appender(size = 1)
        val span = span()
        span.events.add(
            TracingEvent.Span.Event(
                name = "cache.miss",
                timestamp = Instant.fromEpochSeconds(100, 500),
                tags = mapOf("key" to "k1"),
            )
        )
        appender.append(span)

        val events = spansOf(endpoint.requests.receive()).single()["events"]!!.jsonArray
        val event = events.single().jsonObject
        assertThat(event.str("name")).isEqualTo("cache.miss")
        assertThat(event.str("timeUnixNano")).isEqualTo("100000000500")
        assertThat(event.attributes()["key"]!!.str("stringValue")).isEqualTo("k1")
    }

    @Test
    fun errorStatus_synthesizesAnExceptionEvent() = runTest {
        val appender = appender(size = 1)
        appender.append(span(error = IllegalStateException("boom")))

        val span = spansOf(endpoint.requests.receive()).single()
        val status = span["status"]!!.jsonObject
        assertThat(status["code"]!!.jsonPrimitive.int).isEqualTo(2) // ERROR
        assertThat(status.str("message")).isEqualTo("boom")

        val event = span["events"]!!.jsonArray.single().jsonObject
        assertThat(event.str("name")).isEqualTo("exception")
        val attributes = event.attributes()
        assertThat(attributes["exception.type"]!!.str("stringValue")!!).contains("IllegalStateException")
        assertThat(attributes["exception.message"]!!.str("stringValue")).isEqualTo("boom")
        assertThat(attributes["exception.stacktrace"]).isNotNull()
    }

    @Test
    fun explicitExceptionEvent_isNotDuplicated() = runTest {
        val appender = appender(size = 1)
        val span = span(error = IllegalStateException("boom"))
        span.events.add(
            TracingEvent.Span.Event(
                name = "exception",
                timestamp = Instant.fromEpochSeconds(100, 500),
                tags = mapOf("exception.type" to "IllegalStateException"),
            )
        )
        appender.append(span)

        val events = spansOf(endpoint.requests.receive()).single()["events"]!!.jsonArray
        assertThat(events).hasSize(1) // The recorded one only; nothing synthesized.
    }

    @Test
    fun configuredHeaders_areSentWithEveryRequest() = runTest {
        val appender = appender(size = 1, headers = mapOf("x-api-key" to "secret-key"))
        appender.append(span())

        val recorded = endpoint.requests.receive()
        assertThat(recorded.headers["x-api-key"]).isEqualTo("secret-key")
    }

    @Test
    fun unfinishedSpans_areSkipped() = runTest {
        val appender = appender(size = 2)
        appender.append(span(name = "unfinished", finished = false))
        appender.append(span(name = "finished"))

        val spans = spansOf(endpoint.requests.receive())
        assertThat(spans).hasSize(1)
        assertThat(spans.single().str("name")).isEqualTo("finished")
    }

    @Test
    fun batchTimeout_flushesPartialBatches() = runTest {
        val appender = appender(size = 100, timeout = 200.milliseconds)
        appender.append(span())
        // A single span in a batch of 100: only the timeout can flush it.
        assertThat(spansOf(endpoint.requests.receive())).hasSize(1)
    }

    @Test
    fun endpointFailures_doNotKillTheAppender() = runTest {
        val appender = appender(size = 1)
        endpoint.status = HttpStatusCode.InternalServerError
        appender.append(span(name = "rejected"))
        assertThat(endpoint.requests.receive()).isNotNull()

        endpoint.status = HttpStatusCode.OK
        appender.append(span(name = "accepted"))
        assertThat(spansOf(endpoint.requests.receive()).single().str("name")).isEqualTo("accepted")
    }

    @Test
    fun nonHexIds_areHashedToValidOtlpIds() = runTest {
        val appender = appender(size = 1)
        appender.append(span(id = "not-hex-span", traceId = "not-hex-trace"))

        val span = spansOf(endpoint.requests.receive()).single()
        assertThat(span.str("traceId")!!).matches(Regex("[0-9a-f]{32}"))
        assertThat(span.str("spanId")!!).matches(Regex("[0-9a-f]{16}"))
    }

    @Test
    fun sixtyFourBitHexTraceIds_areZeroPadded_notHashed() = runTest {
        val appender = appender(size = 1)
        appender.append(span(traceId = "463AC35C9F6413AD")) // e.g. from a 64-bit B3 carrier.

        val span = spansOf(endpoint.requests.receive()).single()
        assertThat(span.str("traceId")).isEqualTo("0000000000000000463ac35c9f6413ad")
    }

    @Test
    fun allZeroIds_areReplacedWithValidHashedIds() = runTest {
        val appender = appender(size = 1)
        // OTLP reserves the all-zero trace/span id as the invalid sentinel.
        appender.append(span(id = "0000000000000000", traceId = "00000000000000000000000000000000"))

        val span = spansOf(endpoint.requests.receive()).single()
        assertThat(span.str("traceId")!!).matches(Regex("[0-9a-f]{32}"))
        assertThat(span.str("traceId")!!).isNotEqualTo("00000000000000000000000000000000")
        assertThat(span.str("spanId")!!).matches(Regex("[0-9a-f]{16}"))
        assertThat(span.str("spanId")!!).isNotEqualTo("0000000000000000")
    }
}
