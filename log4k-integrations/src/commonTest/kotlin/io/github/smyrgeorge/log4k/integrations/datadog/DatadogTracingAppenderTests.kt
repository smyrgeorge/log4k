package io.github.smyrgeorge.log4k.integrations.datadog

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
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
 * Tests for [DatadogTracingAppender] against a fake agent backed by Ktor's [MockEngine], so the
 * whole suite is in-process (no sockets) and runs on every target. Spans are constructed with
 * fixed ids and timestamps, so the expected wire values are exact; batches are flushed by size
 * (or a short timeout where that is the behavior under test) and the test simply suspends until
 * the request arrives.
 */
class DatadogTracingAppenderTests {

    private class FakeAgent {
        data class Recorded(
            val method: String,
            val url: String,
            val contentType: String?,
            val traceCount: String?,
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
                    traceCount = request.headers["X-Datadog-Trace-Count"],
                    body = request.body.toByteArray().decodeToString(),
                )
            )
            respond(content = "", status = status)
        }
    }

    private val agent = FakeAgent()
    private val tracer = Tracer.of("dd-test-tracer")

    private fun appender(size: Int = 2, timeout: Duration = 60.seconds): DatadogTracingAppender =
        DatadogTracingAppender(
            service = "svc",
            batchSize = size,
            batchTimeout = timeout,
            env = "test-env",
            version = "1.2.3",
            engine = agent.engine,
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
        if (error != null) {
            status = TracingEvent.Span.Status(
                code = TracingEvent.Span.Status.Code.ERROR,
                error = error,
                description = error.message,
            )
        }
    }

    /** The spans of the request body: a flat list over the array-of-traces payload. */
    private fun spansOf(recorded: FakeAgent.Recorded): List<JsonObject> =
        Json.parseToJsonElement(recorded.body).jsonArray.flatMap { trace ->
            trace.jsonArray.map { it.jsonObject }
        }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    @Test
    fun publishesSpans_inDatadogWireFormat() = runTest {
        val appender = appender(size = 2)
        val root = span(name = "root", id = "00000000000000ff")
        val child = span(name = "child", id = "0000000000000f0f", parent = root)
        appender.append(root)
        appender.append(child)

        val recorded = agent.requests.receive()
        assertThat(recorded.method).isEqualTo("PUT")
        assertThat(recorded.url).isEqualTo("http://localhost:8126/v0.4/traces")
        assertThat(recorded.contentType!!).contains("application/json")
        assertThat(recorded.traceCount).isEqualTo("1") // Same trace id -> one trace.

        val spans = spansOf(recorded)
        assertThat(spans).hasSize(2)

        val rootJson = spans.single { it.str("name") == "root" }
        assertThat(rootJson.str("trace_id")).isEqualTo("170") // 0x00000000000000aa
        assertThat(rootJson.str("span_id")).isEqualTo("255") // 0x00000000000000ff
        assertThat(rootJson.str("parent_id")).isNull()
        assertThat(rootJson.str("resource")).isEqualTo("root")
        assertThat(rootJson.str("service")).isEqualTo("svc")
        assertThat(rootJson.str("type")).isEqualTo("custom")
        assertThat(rootJson.str("start")).isEqualTo("100000000250") // 100s + 250ns in nanos.
        assertThat(rootJson.str("duration")).isEqualTo("1000000500") // 1s + 500ns in nanos.
        assertThat(rootJson["error"]!!.jsonPrimitive.int).isEqualTo(0)

        val meta = rootJson["meta"]!!.jsonObject
        assertThat(meta.str("env")).isEqualTo("test-env")
        assertThat(meta.str("version")).isEqualTo("1.2.3")
        assertThat(meta.str("_dd.p.tid")).isEqualTo("1111111111111111") // High 64 bits of the trace id.

        val metrics = rootJson["metrics"]!!.jsonObject
        assertThat(metrics["_sampling_priority_v1"]!!.jsonPrimitive.double).isEqualTo(1.0)

        val childJson = spans.single { it.str("name") == "child" }
        assertThat(childJson.str("parent_id")).isEqualTo("255")
        assertThat(childJson.str("span_id")).isEqualTo("3855") // 0x0000000000000f0f
    }

    @Test
    fun spans_areGroupedByTraceId() = runTest {
        val appender = appender(size = 3)
        appender.append(span(id = "0000000000000001", traceId = "0000000000000000000000000000000a"))
        appender.append(span(id = "0000000000000002", traceId = "0000000000000000000000000000000a"))
        appender.append(span(id = "0000000000000003", traceId = "0000000000000000000000000000000b"))

        val recorded = agent.requests.receive()
        assertThat(recorded.traceCount).isEqualTo("2")
        val traces = Json.parseToJsonElement(recorded.body).jsonArray
        assertThat(traces).hasSize(2)
        assertThat(traces.sumOf { it.jsonArray.size }).isEqualTo(3)
    }

    @Test
    fun errorSpans_carryTheErrorFlagAndMeta() = runTest {
        val appender = appender(size = 1)
        appender.append(span(error = IllegalStateException("boom")))

        val span = spansOf(agent.requests.receive()).single()
        assertThat(span["error"]!!.jsonPrimitive.int).isEqualTo(1)
        val meta = span["meta"]!!.jsonObject
        assertThat(meta.str("error.type")!!).contains("IllegalStateException")
        assertThat(meta.str("error.message")).isEqualTo("boom")
    }

    @Test
    fun numericTags_becomeMetrics_andTheRestBecomeMeta() = runTest {
        val appender = appender(size = 1)
        appender.append(span(tags = mapOf("attempt" to 3, "ratio" to 0.5, "user" to "alice")))

        val span = spansOf(agent.requests.receive()).single()
        val metrics = span["metrics"]!!.jsonObject
        assertThat(metrics["attempt"]!!.jsonPrimitive.double).isEqualTo(3.0)
        assertThat(metrics["ratio"]!!.jsonPrimitive.double).isEqualTo(0.5)
        val meta = span["meta"]!!.jsonObject
        assertThat(meta.str("user")).isEqualTo("alice")
        assertThat(meta["attempt"]).isNull()
    }

    @Test
    fun nonFiniteNumericTags_goToMetaAsStrings_notMetrics() = runTest {
        val appender = appender(size = 1)
        appender.append(
            span(tags = mapOf("nan" to Double.NaN, "inf" to Double.POSITIVE_INFINITY, "count" to 2))
        )

        // Parsing alone is significant: a bare NaN/Infinity literal would be invalid JSON.
        val span = spansOf(agent.requests.receive()).single()
        val metrics = span["metrics"]!!.jsonObject
        assertThat(metrics["nan"]).isNull()
        assertThat(metrics["inf"]).isNull()
        assertThat(metrics["count"]!!.jsonPrimitive.double).isEqualTo(2.0)
        val meta = span["meta"]!!.jsonObject
        assertThat(meta.str("nan")).isEqualTo("NaN")
        assertThat(meta.str("inf")).isEqualTo("Infinity")
    }

    @Test
    fun ddpTid_isLowercased_andSkippedWhenTheTraceIdIsNotFullyHex() = runTest {
        val appender = appender(size = 2)
        appender.append(span(name = "upper", id = "0000000000000001", traceId = "AAAAAAAAAAAAAAAA00000000000000aa"))
        appender.append(span(name = "mixed", id = "0000000000000002", traceId = "1111111111111111zzzzzzzzzzzzzzzz"))

        val spans = spansOf(agent.requests.receive())
        val upper = spans.single { it.str("name") == "upper" }
        assertThat(upper.str("trace_id")).isEqualTo("170") // Low 64 bits parse case-insensitively.
        assertThat(upper["meta"]!!.jsonObject.str("_dd.p.tid")).isEqualTo("aaaaaaaaaaaaaaaa")

        // The low 64 bits were hashed, so the (hex) high bits are unrelated to the reported
        // trace_id — advertising them in `_dd.p.tid` would reconstruct a bogus 128-bit id.
        val mixed = spans.single { it.str("name") == "mixed" }
        assertThat(mixed["meta"]!!.jsonObject["_dd.p.tid"]).isNull()
    }

    @Test
    fun spanEvents_areEncodedAsJsonInMeta() = runTest {
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

        val meta = spansOf(agent.requests.receive()).single()["meta"]!!.jsonObject
        val events = Json.parseToJsonElement(meta.str("events")!!).jsonArray
        val event = events.single().jsonObject
        assertThat(event.str("name")).isEqualTo("cache.miss")
        assertThat(event.str("time_unix_nano")).isEqualTo("100000000500")
        assertThat(event["attributes"]!!.jsonObject.str("key")).isEqualTo("k1")
    }

    @Test
    fun unfinishedSpans_areSkipped() = runTest {
        val appender = appender(size = 2)
        appender.append(span(name = "unfinished", finished = false))
        appender.append(span(name = "finished"))

        val spans = spansOf(agent.requests.receive())
        assertThat(spans).hasSize(1)
        assertThat(spans.single().str("name")).isEqualTo("finished")
    }

    @Test
    fun batchTimeout_flushesPartialBatches() = runTest {
        val appender = appender(size = 100, timeout = 200.milliseconds)
        appender.append(span())
        // A single span in a batch of 100: only the timeout can flush it.
        assertThat(spansOf(agent.requests.receive())).hasSize(1)
    }

    @Test
    fun agentFailures_doNotKillTheAppender() = runTest {
        val appender = appender(size = 1)
        agent.status = HttpStatusCode.InternalServerError
        appender.append(span(name = "rejected"))
        assertThat(agent.requests.receive()).isNotNull()

        agent.status = HttpStatusCode.OK
        appender.append(span(name = "accepted"))
        assertThat(spansOf(agent.requests.receive()).single().str("name")).isEqualTo("accepted")
    }

    @Test
    fun nonHexIds_areHashedToValidNumericIds() = runTest {
        val appender = appender(size = 1)
        val span = span(id = "not-hex-span", traceId = "not-hex-trace")
        span.startAt = Instant.fromEpochSeconds(100)
        span.endAt = Instant.fromEpochSeconds(100) // Zero duration must be coerced to 1ns.
        appender.append(span)

        val json = spansOf(agent.requests.receive()).single()
        assertThat(json.str("trace_id")!!).matches(Regex("[0-9]+"))
        assertThat(json.str("span_id")!!).matches(Regex("[0-9]+"))
        assertThat(json.str("duration")).isEqualTo("1")
    }
}
