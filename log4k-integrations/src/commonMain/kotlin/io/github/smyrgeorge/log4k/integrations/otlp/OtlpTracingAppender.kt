package io.github.smyrgeorge.log4k.integrations.otlp

import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.impl.OpenTelemetryAttributes
import io.github.smyrgeorge.log4k.impl.appenders.BatchAppender
import io.github.smyrgeorge.log4k.integrations.util.epochNanos
import io.github.smyrgeorge.log4k.integrations.util.finishedSpans
import io.github.smyrgeorge.log4k.integrations.util.fnv1a64
import io.github.smyrgeorge.log4k.integrations.util.isHex
import io.github.smyrgeorge.log4k.integrations.util.toName
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * An [io.github.smyrgeorge.log4k.Appender] that publishes finished spans over
 * [OTLP/HTTP](https://opentelemetry.io/docs/specs/otlp/) (`POST /v1/traces`, JSON) — the
 * vendor-neutral OpenTelemetry protocol accepted natively by most tracing backends:
 * the OpenTelemetry Collector, Jaeger, Grafana Tempo, Honeycomb, the Datadog Agent's OTLP
 * ingest, and many more. Register it with `RootLogger.Tracing.appenders.register(...)`.
 *
 * Spans are shipped in batches (via [BatchAppender]): a batch is sent as soon as it reaches
 * [batchSize] spans or when [batchTimeout] elapses since the first span of the batch arrived —
 * so low traffic never holds spans back for long.
 *
 * The HTTP transport is a [Ktor client](https://ktor.io/docs/client-engines.html): pass an
 * [engine] explicitly, or add exactly one Ktor engine artifact for your platform (CIO, Darwin,
 * Curl, WinHttp, Js, ...) to the runtime classpath and leave it `null` so the engine is
 * discovered automatically.
 *
 * Mapping notes:
 * - log4k's span model is already OpenTelemetry-shaped, so the mapping is direct: hex trace and
 *   span ids pass through unchanged (a 16-char/64-bit hex trace id is zero-padded; a
 *   non-hexadecimal or all-zero id is hashed to a stable valid one), events map to span events,
 *   and the status code/description map to the OTLP status.
 * - Tags map to typed attributes: booleans to `boolValue`, integer numbers to `intValue`,
 *   floating-point numbers to `doubleValue`, everything else to `stringValue`.
 * - When a span ends with an error but no explicit `exception` event was recorded, an
 *   `exception` event (`exception.type/message/stacktrace`) is synthesized from the status, per
 *   the OpenTelemetry exception conventions.
 *
 * Delivery is best-effort: endpoint failures are reported to the console, and the batch is
 * dropped — the appender itself keeps running.
 *
 * @param service The `service.name` resource attribute the spans are reported under.
 * @param endpoint Base URL of the OTLP/HTTP endpoint (the standard `/v1/traces` path is
 *                 appended). Defaults to `http://localhost:4318`.
 * @param batchSize Maximum number of spans per request. Defaults to 100.
 * @param batchTimeout Maximum time to hold an incomplete batch. Defaults to 2 seconds.
 * @param env Optional `deployment.environment.name` resource attribute.
 * @param version Optional `service.version` resource attribute.
 * @param headers Extra HTTP headers for every request — most vendors authenticate this way
 *                (e.g. `x-honeycomb-team`, `api-key`, `Authorization`).
 * @param engine Optional Ktor [HttpClientEngine]. When `null`, the engine available on the
 *               classpath is used.
 */
class OtlpTracingAppender(
    private val service: String,
    endpoint: String = "http://localhost:4318",
    batchSize: Int = 100,
    batchTimeout: Duration = 2.seconds,
    private val env: String? = null,
    private val version: String? = null,
    private val headers: Map<String, String> = emptyMap(),
    engine: HttpClientEngine? = null,
) : BatchAppender<TracingEvent>(batchSize, batchTimeout) {

    private val url: String = "${endpoint.trimEnd('/')}/v1/traces"
    private val client: HttpClient = engine?.let { HttpClient(it) } ?: HttpClient()

    override suspend fun handle(event: List<TracingEvent>) {
        val spans = event.finishedSpans()
        if (spans.isEmpty()) return

        // The payload mapping stays inside the try: it renders user-supplied values (tag
        // `toString()`s, throwable class names), and a failure there would otherwise escape to
        // FlowAppender, which swallows it — dropping the batch *silently* instead of reporting it.
        try {
            // ExportTraceServiceRequest: one resource (this service), one scope, all spans in it.
            val payload = buildJsonObject {
                putJsonArray("resourceSpans") {
                    add(buildJsonObject {
                        putJsonObject("resource") {
                            putJsonArray("attributes") {
                                add(attribute("service.name", service))
                                env?.let { add(attribute("deployment.environment.name", it)) }
                                version?.let { add(attribute("service.version", it)) }
                            }
                        }
                        putJsonArray("scopeSpans") {
                            add(buildJsonObject {
                                putJsonObject("scope") { put("name", "log4k") }
                                putJsonArray("spans") { spans.forEach { add(it.toOtlpSpan()) } }
                            })
                        }
                    })
                }
            }

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                this@OtlpTracingAppender.headers.forEach { (key, value) -> header(key, value) }
                setBody(payload.toString())
            }
            if (!response.status.isSuccess()) {
                println("[$name] OTLP endpoint responded with ${response.status.value}; dropped ${spans.size} span(s).")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[$name] Failed to publish ${spans.size} span(s) to the OTLP endpoint: $e")
        }
    }

    private fun TracingEvent.Span.toOtlpSpan(): JsonObject {
        val startAt: Instant = requireNotNull(startAt)
        val endAt: Instant = requireNotNull(endAt)

        return buildJsonObject {
            put("traceId", traceIdHex(context.traceId))
            put("spanId", spanIdHex(context.spanId))
            parent?.let { put("parentSpanId", spanIdHex(it.context.spanId)) }
            put("name", this@toOtlpSpan.name)
            put("kind", SPAN_KIND_INTERNAL)
            // Proto3 JSON mapping: 64-bit integers are encoded as strings.
            put("startTimeUnixNano", startAt.epochNanos().toString())
            put("endTimeUnixNano", endAt.epochNanos().toString())
            if (tags.isNotEmpty()) {
                putJsonArray("attributes") { tags.forEach { (k, v) -> add(attribute(k, v)) } }
            }
            val events = eventsWithSynthesizedException()
            if (events.isNotEmpty()) {
                putJsonArray("events") { events.forEach { add(it.toOtlpEvent()) } }
            }
            putJsonObject("status") {
                put(
                    "code", when (status.code) {
                        TracingEvent.Span.Status.Code.UNSET -> STATUS_CODE_UNSET
                        TracingEvent.Span.Status.Code.OK -> STATUS_CODE_OK
                        TracingEvent.Span.Status.Code.ERROR -> STATUS_CODE_ERROR
                    }
                )
                // Per the OTLP spec, `message` is only meaningful for ERROR statuses.
                if (status.code == TracingEvent.Span.Status.Code.ERROR) {
                    status.description?.let { put("message", it) }
                }
            }
        }
    }

    /**
     * The span's events, appending an `exception` event derived from the error status when the
     * span ended with an error but none was recorded explicitly (e.g. `end(error)` without a
     * `span.exception(error)` call) — so the stack trace is never lost.
     */
    private fun TracingEvent.Span.eventsWithSynthesizedException(): List<TracingEvent.Span.Event> {
        val error = status.error ?: return events
        // The same constants core's `Span.exception()` records with, so the dedup check cannot
        // drift from what an explicitly recorded exception event is actually named.
        if (events.any { it.name == OpenTelemetryAttributes.EXCEPTION }) return events
        val synthesized = TracingEvent.Span.Event(
            name = OpenTelemetryAttributes.EXCEPTION,
            timestamp = requireNotNull(endAt),
            tags = mapOf(
                OpenTelemetryAttributes.EXCEPTION_TYPE to error::class.toName(),
                OpenTelemetryAttributes.EXCEPTION_MESSAGE to (error.message ?: ""),
                OpenTelemetryAttributes.EXCEPTION_STACKTRACE to error.stackTraceToString(),
            ),
        )
        return events + synthesized
    }

    private fun TracingEvent.Span.Event.toOtlpEvent(): JsonObject = buildJsonObject {
        put("name", this@toOtlpEvent.name)
        put("timeUnixNano", timestamp.epochNanos().toString())
        if (tags.isNotEmpty()) {
            putJsonArray("attributes") { tags.forEach { (k, v) -> add(attribute(k, v)) } }
        }
    }

    private companion object {
        // https://opentelemetry.io/docs/specs/otel/trace/api/#spankind
        private const val SPAN_KIND_INTERNAL = 1

        // https://opentelemetry.io/docs/specs/otel/trace/api/#set-status
        private const val STATUS_CODE_UNSET = 0
        private const val STATUS_CODE_OK = 1
        private const val STATUS_CODE_ERROR = 2

        /** Doubles above 2^53 - 1 cannot represent integers exactly; keep them as doubles. */
        private const val MAX_SAFE_INTEGER = 9007199254740991.0

        /** A typed OTLP `KeyValue`: booleans, integers, and floats keep their type. */
        private fun attribute(key: String, value: Any): JsonObject = buildJsonObject {
            put("key", key)
            putJsonObject("value") {
                when (value) {
                    is Boolean -> put("boolValue", value)
                    // Proto3 JSON mapping: 64-bit integers are encoded as strings. Kotlin/JS
                    // represents every number as a double at runtime, making `is Int`-style
                    // dispatch unreliable there — so dispatch on the value instead, producing
                    // identical output on all targets: integral numbers become `intValue`.
                    is Long -> put("intValue", value.toString())
                    is Number -> {
                        val double = value.toDouble()
                        val integral = double.isFinite()
                                && double == kotlin.math.floor(double)
                                && kotlin.math.abs(double) <= MAX_SAFE_INTEGER
                        when {
                            integral -> put("intValue", double.toLong().toString())
                            // Proto3 JSON encodes non-finite doubles as the strings "NaN",
                            // "Infinity" and "-Infinity" — a bare literal would be invalid
                            // JSON and poison the whole batch.
                            !double.isFinite() -> put(
                                "doubleValue",
                                if (double.isNaN()) "NaN" else if (double > 0) "Infinity" else "-Infinity",
                            )

                            else -> put("doubleValue", double)
                        }
                    }

                    else -> put("stringValue", value.toString())
                }
            }
        }

        /**
         * OTLP trace ids are exactly 32 lowercase hex chars, and the all-zero id is reserved as
         * the invalid sentinel. A 16-char hex id — the 64-bit form used by e.g., B3 carriers —
         * is zero-padded, so the correlation with the upstream trace is preserved; anything else
         * is hashed.
         */
        private fun traceIdHex(id: String): String = when (id.length) {
            32 if id.isHex() && id.any { it != '0' } -> id.lowercase()
            16 if id.isHex() && id.any { it != '0' } -> "0000000000000000" + id.lowercase()
            else -> "0000000000000000" + fnv1a64(id).toString(16).padStart(16, '0')
        }

        /**
         * OTLP span ids are exactly 16 lowercase hex chars, and the all-zero id is reserved as
         * the invalid sentinel; anything else is hashed.
         */
        private fun spanIdHex(id: String): String =
            if (id.length == 16 && id.isHex() && id.any { it != '0' }) id.lowercase()
            else fnv1a64(id).toString(16).padStart(16, '0')
    }
}
