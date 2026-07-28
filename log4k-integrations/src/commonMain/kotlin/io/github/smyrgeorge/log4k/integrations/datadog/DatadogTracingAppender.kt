package io.github.smyrgeorge.log4k.integrations.datadog

import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.impl.appenders.BatchAppender
import io.github.smyrgeorge.log4k.integrations.epochNanos
import io.github.smyrgeorge.log4k.integrations.finishedSpans
import io.github.smyrgeorge.log4k.integrations.fnv1a64
import io.github.smyrgeorge.log4k.integrations.isHex
import io.github.smyrgeorge.log4k.integrations.nonZero
import io.github.smyrgeorge.log4k.integrations.toName
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * An [io.github.smyrgeorge.log4k.Appender] that publishes finished spans to a local
 * [Datadog Agent](https://docs.datadoghq.com/agent/) over its APM trace intake API
 * (`PUT /v0.4/traces`, JSON). Register it with `RootLogger.Tracing.appenders.register(...)`.
 *
 * Spans are shipped in batches (via [BatchAppender]): a batch is sent as soon as it reaches
 * [batchSize] spans or when [batchTimeout] elapses since the first span of the batch arrived —
 * so low traffic never holds spans back for long. Within a batch, spans are grouped by trace,
 * as the intake API expects.
 *
 * The HTTP transport is a [Ktor client](https://ktor.io/docs/client-engines.html): pass an
 * [engine] explicitly, or add exactly one Ktor engine artifact for your platform (CIO, Darwin,
 * Curl, WinHttp, Js, ...) to the runtime classpath and leave it `null` so the engine is
 * discovered automatically.
 *
 * The agent must have APM enabled (`DD_APM_ENABLED=true`); it listens on port `8126` by default.
 *
 * Mapping notes:
 * - log4k's OpenTelemetry-style hex ids are converted to Datadog's numeric ids: the low 64 bits
 *   are used, and for 128-bit trace ids the high 64 bits are preserved in the `_dd.p.tid` tag.
 *   Non-hexadecimal ids (e.g. from a remote/propagated context) are hashed (FNV-1a).
 * - Finite numeric span tags become Datadog `metrics`; everything else — including non-finite
 *   numbers, which `metrics` cannot represent — goes to `meta` (stringified).
 * - Span events are encoded as a JSON array in `meta.events`, mirroring the OpenTelemetry
 *   ingestion convention. An error status sets `error=1` plus `error.type/message/stack`.
 *
 * Delivery is best-effort: agent failures are reported to the console and the batch is
 * dropped — the appender itself keeps running.
 *
 * @param service The Datadog service name the spans are reported under.
 * @param agentUrl Base URL of the local Datadog Agent. Defaults to `http://localhost:8126`.
 * @param batchSize Maximum number of spans per request. Defaults to 100.
 * @param batchTimeout Maximum time to hold an incomplete batch. Defaults to 2 seconds.
 * @param env Optional `env` tag applied to every span.
 * @param version Optional `version` tag applied to every span.
 * @param spanType Datadog span type (e.g. `web`, `db`, `custom`). Defaults to `custom`.
 * @param engine Optional Ktor [HttpClientEngine]. When `null`, the engine available on the
 *               classpath is used.
 */
class DatadogTracingAppender(
    private val service: String,
    agentUrl: String = "http://localhost:8126",
    batchSize: Int = 100,
    batchTimeout: Duration = 2.seconds,
    private val env: String? = null,
    private val version: String? = null,
    private val spanType: String = "custom",
    engine: HttpClientEngine? = null,
) : BatchAppender<TracingEvent>(batchSize, batchTimeout) {

    private val endpoint: String = "${agentUrl.trimEnd('/')}/v0.4/traces"
    private val client: HttpClient = engine?.let { HttpClient(it) } ?: HttpClient()

    override suspend fun handle(event: List<TracingEvent>) {
        val spans = event.finishedSpans()
        if (spans.isEmpty()) return

        // The intake API expects an array of traces, each an array of its spans.
        val traces = spans.groupBy { it.context.traceId }.values
        val payload = buildJsonArray {
            traces.forEach { trace ->
                add(buildJsonArray { trace.forEach { add(it.toDatadogSpan()) } })
            }
        }

        try {
            val response = client.put(endpoint) {
                contentType(ContentType.Application.Json)
                header("Datadog-Meta-Lang", "kotlin")
                header("Datadog-Meta-Tracer-Version", "log4k")
                header("X-Datadog-Trace-Count", traces.size.toString())
                setBody(payload.toString())
            }
            if (!response.status.isSuccess()) {
                println("[$name] Datadog agent responded with ${response.status.value}; dropped ${spans.size} span(s).")
            }
        } catch (e: Exception) {
            println("[$name] Failed to publish ${spans.size} span(s) to the Datadog agent: $e")
        }
    }

    private fun TracingEvent.Span.toDatadogSpan(): JsonObject {
        val startAt: Instant = requireNotNull(startAt)
        val endAt: Instant = requireNotNull(endAt)

        val meta = mutableMapOf<String, String>()
        val metrics = mutableMapOf<String, Double>()
        tags.forEach { (key, value) ->
            // Metrics must be finite float64s: a bare `NaN`/`Infinity` would render as an
            // invalid JSON literal and poison the whole batch at the agent.
            val metric = (value as? Number)?.toDouble()
            if (metric != null && metric.isFinite()) metrics[key] = metric
            else meta[key] = value.toString()
        }
        // Datadog ids are 64-bit; keep the high half of a 128-bit trace id in `_dd.p.tid`
        // (16 lowercase hex chars) — but only when the full id is hex: otherwise `trace_id`
        // is a hash and the high bits are unrelated to it.
        if (context.traceId.length == 32 && context.traceId.isHex()) {
            val high = context.traceId.substring(0, 16)
            if (high.any { it != '0' }) meta["_dd.p.tid"] = high.lowercase()
        }
        env?.let { meta["env"] = it }
        version?.let { meta["version"] = it }
        status.error?.let {
            meta["error.type"] = it::class.toName()
            meta["error.message"] = it.message ?: ""
            meta["error.stack"] = it.stackTraceToString()
        } ?: status.description?.let {
            if (status.code == TracingEvent.Span.Status.Code.ERROR) meta["error.message"] = it
        }
        if (events.isNotEmpty()) meta["events"] = eventsAsJson()

        return buildJsonObject {
            put("trace_id", uint64(traceIdLow64(context.traceId)))
            put("span_id", uint64(idToUint64(context.spanId)))
            parent?.let { put("parent_id", uint64(idToUint64(it.context.spanId))) }
            put("name", this@toDatadogSpan.name)
            put("resource", this@toDatadogSpan.name)
            put("service", service)
            put("type", spanType)
            put("start", startAt.epochNanos())
            put("duration", (endAt - startAt).inWholeNanoseconds.coerceAtLeast(1))
            put("error", if (status.code == TracingEvent.Span.Status.Code.ERROR) 1 else 0)
            putJsonObject("meta") { meta.forEach { (k, v) -> put(k, v) } }
            putJsonObject("metrics") {
                metrics.forEach { (k, v) -> put(k, v) }
                // AUTO_KEEP: the decision to sample belongs to the agent, not this appender.
                put("_sampling_priority_v1", 1.0)
            }
        }
    }

    private fun TracingEvent.Span.eventsAsJson(): String = buildJsonArray {
        events.forEach { event ->
            add(buildJsonObject {
                put("name", event.name)
                put("time_unix_nano", event.timestamp.epochNanos())
                if (event.tags.isNotEmpty()) {
                    putJsonObject("attributes") {
                        event.tags.forEach { (k, v) -> put(k, v.toString()) }
                    }
                }
            })
        }
    }.toString()

    private companion object {
        /** Emits a uint64 as a raw JSON number, which `Long` cannot represent above 2^63-1. */
        private fun uint64(value: ULong) = JsonUnquotedLiteral(value.toString())

        /** Low 64 bits of a (possibly 128-bit) hex trace id; non-hex ids are hashed instead. */
        private fun traceIdLow64(traceId: String): ULong =
            traceId.takeLast(16).toULongOrNull(16)?.nonZero() ?: fnv1a64(traceId)

        private fun idToUint64(id: String): ULong = id.toULongOrNull(16)?.nonZero() ?: fnv1a64(id)
    }
}
