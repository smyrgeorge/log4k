package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.impl.Tags
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.time.Instant

/**
 * An [Appender] that aggregates [MeteringEvent]s in memory and can render them as an
 * OpenMetrics/Prometheus exposition string via [toOpenMetricsLineFormatString].
 *
 * Incoming events are folded into a per-instrument-and-tag-set [Instrument] held in [registry]:
 * `CreateInstrument` registers an instrument's metadata (see [Instrument.Info]), while the value
 * events (`Set`/`Increment`/`Decrement`/`Record`) mutate the matching aggregate. The aggregation
 * model mirrors the OpenTelemetry instruments produced by [Meter].
 *
 * - OpenTelemetry metrics: https://opentelemetry.io/docs/specs/otel/metrics/api/
 * - OpenMetrics: https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
 */
class SimpleMeteringCollectorAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()

    // The aggregated value per instrument-and-tag-set, keyed by `MeteringEvent.key()`.
    private val registry: MutableMap<Int, Instrument> = mutableMapOf()

    // The metadata registered by `CreateInstrument`, keyed by instrument name.
    private val instruments: MutableMap<String, Instrument.Info> = mutableMapOf()

    override suspend fun append(event: MeteringEvent) {
        when (event) {
            is MeteringEvent.CreateInstrument -> {
                if (instruments.containsKey(event.name)) return
                Instrument.Info(
                    name = event.name,
                    kind = event.kind,
                    unit = event.unit,
                    description = event.description
                ).also { instruments[event.name] = it }
            }

            is MeteringEvent.Set -> {
                when (val instrument = event.instrument()) {
                    is Instrument.Counter -> instrument.set(event)
                    is Instrument.UpDownCounter -> instrument.set(event)
                    else -> Unit
                }
            }

            is MeteringEvent.Increment -> {
                when (val instrument = event.instrument()) {
                    is Instrument.Counter -> instrument.increment(event)
                    is Instrument.UpDownCounter -> instrument.increment(event)
                    else -> Unit
                }
            }

            is MeteringEvent.Decrement -> {
                when (val instrument = event.instrument()) {
                    is Instrument.UpDownCounter -> instrument.decrement(event)
                    else -> Unit
                }
            }

            is MeteringEvent.Record -> {
                when (val instrument = event.instrument()) {
                    is Instrument.Gauge -> instrument.record(event)
                    is Instrument.Histogram -> instrument.record(event)
                    else -> Unit
                }
            }
        }
    }

    /**
     * Generates a string representation of the metrics in a format suitable for Prometheus.
     * https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exposition_formats.md
     * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
     *
     * @return An OpenMetrics-compliant string representation of the collected metrics.
     */
    fun toOpenMetricsLineFormatString(): String = buildString {
        registry
            .values
            .sortedBy { it.name }
            .groupBy { it.name }
            .forEach { group ->
                val instruments: List<Instrument> = group.value
                val header: String = instruments.first().openMetricsHeaderString()
                append(header)
                instruments
                    .sortedBy { it.sortKey() }
                    .forEach {
                        append(it.openMetricsValueString())
                    }
                appendLine()
            }
    }

    /**
     * Converts a `MeteringEvent.ValueEvent` into an `Instrument`.
     *
     * This method checks for an existing instrument in the registry using the event's key.
     * If it does not exist, it creates a new instrument based on the information
     * provided in the event and adds it to the registry. The type of instrument created
     * depends on the `kind` property of the event's associated instrument information.
     *
     * @return The existing or newly created `Instrument`, or null if the event's
     *         associated instrument information is not found or unsupported.
     */
    private fun MeteringEvent.ValueEvent.instrument(): Instrument? {
        val key: Int = key()
        val existing: Instrument? = registry[key]
        return if (existing == null) {
            val info = instruments[name] ?: return null
            when (info.kind) {
                Meter.Instrument.Kind.Counter -> Instrument.Counter(
                    name = name,
                    tags = tags,
                    kind = info.kind,
                    unit = info.unit,
                    description = info.description,
                    value = 0,
                    updatedAt = timestamp
                )

                Meter.Instrument.Kind.UpDownCounter -> Instrument.UpDownCounter(
                    name = name,
                    tags = tags,
                    kind = info.kind,
                    unit = info.unit,
                    description = info.description,
                    value = 0,
                    updatedAt = timestamp
                )

                Meter.Instrument.Kind.Gauge -> Instrument.Gauge(
                    name = name,
                    tags = tags,
                    kind = info.kind,
                    unit = info.unit,
                    description = info.description,
                    value = 0,
                    updatedAt = timestamp
                )

                Meter.Instrument.Kind.Histogram -> Instrument.Histogram(
                    name = name,
                    tags = tags,
                    kind = info.kind,
                    unit = info.unit,
                    description = info.description,
                    value = 0,
                    updatedAt = timestamp
                )
            }.also { registry[key] = it }
        } else existing
    }

    /**
     * The in-memory aggregate for a single instrument-and-tag-set, mirroring an OpenTelemetry
     * instrument. Each implementation knows how to fold its value events and how to render itself
     * as OpenMetrics.
     *
     * - OpenTelemetry instruments: https://opentelemetry.io/docs/specs/otel/metrics/api/#meter
     * - OpenMetrics: https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
     */
    sealed interface Instrument {
        val name: String
        val kind: Meter.Instrument.Kind
        val unit: String?
        val description: String?
        val tags: Tags?
        var value: Number
        var updatedAt: Instant?

        /** Orders series that share a name (one per tag-set) deterministically within a `# TYPE` block. */
        fun sortKey(): Int = tags.hashCode()

        /**
         * The OpenMetrics metadata block (`# HELP`, `# UNIT`, `# TYPE`) emitted once per instrument name.
         * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#metricfamily
         */
        fun openMetricsHeaderString(): String = buildString {
            description?.let { append("# HELP ").append(name).append(" ").append(it).appendLine() }
            unit?.let { append("# UNIT ").append(name).append(" ").append(it).appendLine() }
            append("# TYPE ").append(name).append(" ").append(kind.name.lowercase()).appendLine()
        }

        /**
         * The OpenMetrics sample line(s) for this series: `name{tags} value [updatedAt]`.
         * Overridden by multi-line instruments such as [Histogram].
         * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#sample
         */
        fun openMetricsValueString(): String = buildString {
            append(name)
            tags?.let { append(it.format()) }
            append(" ").append(value)
            updatedAt?.let { append(" ").append(it.epochSeconds) }
            appendLine()
        }

        /** Renders the tags as an OpenMetrics label set, e.g. `{key="value",…}`. */
        fun Tags.format(): String =
            entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=\"$v\"" }

        /** Immutable metadata for an instrument, registered from a [MeteringEvent.CreateInstrument]. */
        data class Info(
            val name: String,
            val kind: Meter.Instrument.Kind,
            val unit: String?,
            val description: String?,
        )

        /**
         * Base for cumulative counter-style aggregates: the value is either overwritten ([set]) or
         * increased ([increment]) as events arrive.
         */
        abstract class AbstractCounter(
            override val name: String,
            override val tags: Tags?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override var value: Number,
            override var updatedAt: Instant? = null,
        ) : Instrument {
            /** Overwrites the current value (used for the absolute `set` operation). */
            fun set(event: MeteringEvent.Set) {
                updatedAt = event.timestamp
                value = event.value
            }

            /** Adds the event's value to the current one, preserving its numeric type. */
            fun increment(event: MeteringEvent.Increment) {
                updatedAt = event.timestamp
                when (event.value) {
                    is Int -> value = value.toInt() + event.value
                    is Long -> value = value.toLong() + event.value
                    is Float -> value = value.toFloat() + event.value
                    is Double -> value = value.toDouble() + event.value
                }
            }
        }

        /**
         * Base for last-value aggregates: each observation replaces the previous value. Used by
         * [Gauge]; [Histogram] aggregates its observations differently and does not extend this.
         */
        abstract class AbstractRecorder(
            override val name: String,
            override val tags: Tags?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override var value: Number,
            override var updatedAt: Instant? = null,
        ) : Instrument {
            /** Replaces the current value with the latest observation. */
            fun record(event: MeteringEvent.Record) {
                updatedAt = event.timestamp
                value = event.value
            }
        }

        /**
         * A monotonically increasing counter (only ever `set` or `increment`ed).
         *
         * - OpenTelemetry: https://opentelemetry.io/docs/specs/otel/metrics/api/#counter
         * - Prometheus: https://prometheus.io/docs/concepts/metric_types/#counter
         */
        class Counter(
            name: String,
            tags: Tags?,
            kind: Meter.Instrument.Kind,
            unit: String?,
            description: String?,
            value: Number,
            updatedAt: Instant? = null,
        ) : AbstractCounter(name, tags, kind, unit, description, value, updatedAt)

        /**
         * A counter that can also go down, adding [decrement] to the [AbstractCounter] operations.
         *
         * - OpenTelemetry: https://opentelemetry.io/docs/specs/otel/metrics/api/#updowncounter
         */
        class UpDownCounter(
            name: String,
            tags: Tags?,
            kind: Meter.Instrument.Kind,
            unit: String?,
            description: String?,
            value: Number,
            updatedAt: Instant? = null,
        ) : AbstractCounter(name, tags, kind, unit, description, value, updatedAt) {
            /** Subtracts the event's value from the current one; a no-op for a plain (monotonic) counter. */
            fun decrement(event: MeteringEvent.Decrement) {
                if (kind == Meter.Instrument.Kind.Counter) return
                updatedAt = event.timestamp
                when (event.value) {
                    is Int -> value = value.toInt() - event.value
                    is Long -> value = value.toLong() - event.value
                    is Float -> value = value.toFloat() - event.value
                    is Double -> value = value.toDouble() - event.value
                }
            }
        }

        /**
         * A gauge that tracks the latest recorded value (it can arbitrarily rise and fall).
         *
         * - OpenTelemetry: https://opentelemetry.io/docs/specs/otel/metrics/api/#gauge
         * - Prometheus: https://prometheus.io/docs/concepts/metric_types/#gauge
         */
        class Gauge(
            name: String,
            tags: Tags?,
            kind: Meter.Instrument.Kind,
            unit: String?,
            description: String?,
            value: Number,
            updatedAt: Instant? = null,
        ) : AbstractRecorder(name, tags, kind, unit, description, value, updatedAt)

        /**
         * Aggregates sampled observations into a running `count` and `sum`. Unlike a gauge (which
         * keeps only the last value), a histogram accumulates every recorded value.
         *
         * - OpenTelemetry: https://opentelemetry.io/docs/specs/otel/metrics/api/#histogram
         * - Prometheus: https://prometheus.io/docs/concepts/metric_types/#histogram
         */
        class Histogram(
            override val name: String,
            override val tags: Tags?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override var value: Number,
            override var updatedAt: Instant? = null,
        ) : Instrument {
            // Running aggregates over the observed values.
            private var count: Long = 0
            private var sum: Double = 0.0

            fun record(event: MeteringEvent.Record) {
                updatedAt = event.timestamp
                count += 1
                sum += event.value.toDouble()
                value = count // keep the interface's `value` in sync with the observation count.
            }

            /**
             * A histogram is exposed as its cumulative `+Inf` bucket plus the mandatory `_sum` and
             * `_count` series.
             * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#histogram
             */
            override fun openMetricsValueString(): String = buildString {
                val ts = updatedAt?.let { " ${it.epochSeconds}" } ?: ""
                val tagStr = tags?.format() ?: ""
                val bucketTags = ((tags ?: emptyMap()) + ("le" to "+Inf")).format()
                append(name).append("_bucket").append(bucketTags).append(" ").append(count).append(ts).appendLine()
                append(name).append("_sum").append(tagStr).append(" ").append(sum).append(ts).appendLine()
                append(name).append("_count").append(tagStr).append(" ").append(count).append(ts).appendLine()
            }
        }
    }
}
