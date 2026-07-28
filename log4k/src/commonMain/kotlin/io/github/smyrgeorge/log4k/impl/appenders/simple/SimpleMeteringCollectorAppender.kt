package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.impl.Tags
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleMeteringCollectorAppender.Companion.promotingPlus
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlin.time.Instant

/**
 * An [Appender] that aggregates [MeteringEvent]s in memory and can render them as an
 * OpenMetrics/Prometheus exposition string via [toOpenMetricsLineFormatString].
 *
 * Incoming events are folded into a per-instrument-and-tag-set [Instrument] held in [registry]:
 * `CreateInstrument` registers an instrument's metadata (see [Instrument.Info]), while the value
 * events (`Set`/`Increment`/`Decrement`/`Record`) produce an updated aggregate. The aggregation
 * model mirrors the OpenTelemetry instruments produced by [Meter].
 *
 * Both registries are copy-on-write snapshots behind [AtomicReference]s and every [Instrument] is
 * immutable: `append` (running on the RootLogger consumer coroutine) publishes a fresh snapshot per
 * event, so a scraping thread calling [toOpenMetricsLineFormatString] concurrently always renders a
 * consistent snapshot — never a map being mutated underneath it, and never a torn aggregate (e.g. a
 * histogram whose `count` was updated but whose `sum` was not).
 *
 * - OpenTelemetry metrics: https://opentelemetry.io/docs/specs/otel/metrics/api/
 * - OpenMetrics: https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
 */
@OptIn(ExperimentalAtomicApi::class)
class SimpleMeteringCollectorAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()

    // The aggregated value per instrument-and-tag-set, keyed by the (name, tags) pair itself:
    // hash-based keys (Int hashes of name+tags) can collide and silently merge distinct series.
    private val registry: AtomicReference<Map<Pair<String, Tags>, Instrument>> = AtomicReference(emptyMap())

    // The metadata registered by `CreateInstrument`, keyed by instrument name.
    private val instruments: AtomicReference<Map<String, Instrument.Info>> = AtomicReference(emptyMap())

    override suspend fun append(event: MeteringEvent) {
        when (event) {
            is MeteringEvent.CreateInstrument -> instruments.update { current ->
                if (event.name in current) current
                else current + (event.name to Instrument.Info(
                    name = event.name,
                    kind = event.kind,
                    unit = event.unit,
                    description = event.description,
                ))
            }

            is MeteringEvent.ValueEvent -> registry.update { current -> event.foldInto(current) }
        }
    }

    /**
     * Generates a string representation of the metrics in the OpenMetrics line format.
     * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
     *
     * Metric and label names are sanitized to the exposition-format alphabet, counter samples carry
     * the mandatory `_total` suffix, units are appended to the family name (OpenMetrics requires the
     * unit to be a suffix of the name), and the exposition is terminated with `# EOF`.
     *
     * Safe to call from any thread while events are being appended: the exposition is rendered from
     * one immutable snapshot of the registry.
     *
     * Sample timestamps are intentionally not emitted: Prometheus interprets explicitly-timestamped
     * samples relative to its own time window and drops stale ones, so — like most exporters — the
     * collector lets the scraper assign timestamps. This also keeps the output parseable by the
     * legacy Prometheus text format, whose timestamps are milliseconds (OpenMetrics uses seconds).
     *
     * @return An OpenMetrics-compliant string representation of the collected metrics.
     */
    fun toOpenMetricsLineFormatString(): String = buildString {
        registry
            .load()
            .values
            .groupBy { it.openMetricsName() }
            .entries
            .sortedBy { it.key }
            .forEach { (family, series) ->
                append(series.first().openMetricsHeaderString(family))
                series
                    .sortedBy { it.sortKey() }
                    .forEach { append(it.openMetricsValueString(family)) }
            }
        append("# EOF").appendLine()
    }

    /**
     * Folds this value event into the [current] registry snapshot, returning the next snapshot.
     *
     * The matching aggregate is looked up by the event's instrument name and tag-set; when absent,
     * a zero-valued one is created from the metadata registered by `CreateInstrument`. The event is
     * then applied to it, producing a *new* immutable aggregate that replaces the old one in the
     * returned map. The [current] snapshot is returned unchanged when the event cannot be applied:
     * unknown instrument metadata, or a mismatched event/kind combination (e.g. a `Record` against
     * a counter) — which must not leave a phantom zero-valued series behind either.
     */
    private fun MeteringEvent.ValueEvent.foldInto(
        current: Map<Pair<String, Tags>, Instrument>,
    ): Map<Pair<String, Tags>, Instrument> {
        val key: Pair<String, Tags> = name to tags
        val existing: Instrument = current[key] ?: newInstrument() ?: return current
        val next: Instrument = when (this) {
            is MeteringEvent.Set -> when (existing) {
                is Instrument.Counter -> existing.set(this)
                is Instrument.UpDownCounter -> existing.set(this)
                else -> return current
            }

            is MeteringEvent.Increment -> when (existing) {
                is Instrument.Counter -> existing.increment(this)
                is Instrument.UpDownCounter -> existing.increment(this)
                else -> return current
            }

            is MeteringEvent.Decrement -> when (existing) {
                is Instrument.UpDownCounter -> existing.decrement(this)
                else -> return current
            }

            is MeteringEvent.Record -> when (existing) {
                is Instrument.Gauge -> existing.record(this)
                is Instrument.Histogram -> existing.record(this)
                else -> return current
            }
        }
        return current + (key to next)
    }

    /**
     * Creates the zero-valued aggregate for this event's instrument, or `null` if the event's
     * instrument metadata is unknown or the event cannot be applied to instruments of that kind.
     */
    private fun MeteringEvent.ValueEvent.newInstrument(): Instrument? {
        val info = instruments.load()[name] ?: return null
        // Only register an aggregate the event can actually be applied to: a mismatched
        // event/kind combination (e.g. a `Record` against a counter) must not leave a
        // phantom zero-valued series behind.
        if (!appliesTo(info.kind)) return null
        return when (info.kind) {
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
                updatedAt = timestamp
            )
        }
    }

    /** Whether this event can be applied to an instrument of the given [kind] (mirrors the dispatch in [foldInto]). */
    private fun MeteringEvent.ValueEvent.appliesTo(kind: Meter.Instrument.Kind): Boolean = when (this) {
        is MeteringEvent.Set, is MeteringEvent.Increment ->
            kind == Meter.Instrument.Kind.Counter || kind == Meter.Instrument.Kind.UpDownCounter

        is MeteringEvent.Decrement -> kind == Meter.Instrument.Kind.UpDownCounter
        is MeteringEvent.Record -> kind == Meter.Instrument.Kind.Gauge || kind == Meter.Instrument.Kind.Histogram
    }

    /**
     * The **immutable** in-memory aggregate for a single instrument-and-tag-set, mirroring an
     * OpenTelemetry instrument. Each implementation knows how to fold its value events — producing
     * a new aggregate — and how to render itself as OpenMetrics. Immutability is what makes the
     * copy-on-write registry snapshots safe to render from any thread.
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
        val value: Number
        val updatedAt: Instant?

        /** Orders series that share a name (one per tag-set) deterministically within a `# TYPE` block. */
        fun sortKey(): Int = tags.hashCode()

        /**
         * The OpenMetrics MetricFamily name for this instrument: the sanitized [name], with a
         * counter's `_total` suffix stripped (it is re-added on the sample line) and the sanitized
         * [unit] appended when not already present — OpenMetrics requires the unit to be a suffix
         * of the family name.
         */
        fun openMetricsName(): String {
            var family = name.sanitizeMetricName()
            if (kind == Meter.Instrument.Kind.Counter) family = family.removeSuffix("_total")
            unit?.sanitizeNameChars()?.takeIf { it.isNotEmpty() }?.let {
                if (!family.endsWith("_$it")) family = "${family}_$it"
            }
            return family.ifEmpty { "_" }
        }

        /**
         * The OpenMetrics type of this instrument. An `UpDownCounter` is exposed as a `gauge`,
         * mirroring the OpenTelemetry-to-Prometheus mapping — `updowncounter` is not a valid
         * exposition type, and the value is non-monotonic.
         */
        fun openMetricsType(): String = when (kind) {
            Meter.Instrument.Kind.Counter -> "counter"
            Meter.Instrument.Kind.UpDownCounter -> "gauge"
            Meter.Instrument.Kind.Gauge -> "gauge"
            Meter.Instrument.Kind.Histogram -> "histogram"
        }

        /**
         * The OpenMetrics metadata block (`# TYPE`, `# UNIT`, `# HELP`) emitted once per metric family.
         * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#metricfamily
         */
        fun openMetricsHeaderString(family: String): String = buildString {
            append("# TYPE ").append(family).append(" ").append(openMetricsType()).appendLine()
            unit?.sanitizeNameChars()?.takeIf { it.isNotEmpty() }?.let {
                append("# UNIT ").append(family).append(" ").append(it).appendLine()
            }
            description?.let {
                append("# HELP ").append(family).append(" ").append(it.escapeHelpText()).appendLine()
            }
        }

        /**
         * The OpenMetrics sample line(s) for this series: `name{tags} value`. Counter samples get
         * the mandatory `_total` suffix. Overridden by multi-line instruments such as [Histogram].
         * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#sample
         */
        fun openMetricsValueString(family: String): String = buildString {
            append(family)
            if (kind == Meter.Instrument.Kind.Counter) append("_total")
            tags?.let { append(it.format()) }
            append(" ").append(value)
            appendLine()
        }

        /**
         * Renders the tags as an OpenMetrics label set, e.g. `{key="value",…}`: label names are
         * sanitized and label values escaped per the exposition format.
         */
        fun Tags.format(): String = entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) ->
            "${k.sanitizeLabelName()}=\"${v.toString().escapeLabelValue()}\""
        }

        /** Immutable metadata for an instrument, registered from a [MeteringEvent.CreateInstrument]. */
        data class Info(
            val name: String,
            val kind: Meter.Instrument.Kind,
            val unit: String?,
            val description: String?,
        )

        /**
         * A monotonically increasing counter: the value is either overwritten ([set]) or increased
         * ([increment]) — each fold produces a new aggregate.
         *
         * - OpenTelemetry: https://opentelemetry.io/docs/specs/otel/metrics/api/#counter
         * - Prometheus: https://prometheus.io/docs/concepts/metric_types/#counter
         */
        class Counter(
            override val name: String,
            override val tags: Tags?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override val value: Number,
            override val updatedAt: Instant? = null,
        ) : Instrument {
            /** Returns a copy with the value overwritten (used for the absolute `set` operation). */
            fun set(event: MeteringEvent.Set): Counter =
                Counter(name, tags, kind, unit, description, event.value, event.timestamp)

            /**
             * Returns a copy with the event's value added to the current one, promoting to the wider
             * of the two numeric types; integral accumulation is widened to `Long`, so an `Int`-fed
             * series does not wrap at 2^31 and no event is silently dropped.
             */
            fun increment(event: MeteringEvent.Increment): Counter =
                Counter(name, tags, kind, unit, description, value.promotingPlus(event.value), event.timestamp)
        }

        /**
         * A counter that can also go down, adding [decrement] to the [Counter]-style operations.
         *
         * - OpenTelemetry: https://opentelemetry.io/docs/specs/otel/metrics/api/#updowncounter
         */
        class UpDownCounter(
            override val name: String,
            override val tags: Tags?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override val value: Number,
            override val updatedAt: Instant? = null,
        ) : Instrument {
            /** Returns a copy with the value overwritten (used for the absolute `set` operation). */
            fun set(event: MeteringEvent.Set): UpDownCounter =
                UpDownCounter(name, tags, kind, unit, description, event.value, event.timestamp)

            /** Returns a copy with the event's value added (same numeric promotion as [Counter.increment]). */
            fun increment(event: MeteringEvent.Increment): UpDownCounter =
                UpDownCounter(name, tags, kind, unit, description, value.promotingPlus(event.value), event.timestamp)

            /** Returns a copy with the event's value subtracted (same numeric promotion as [increment]). */
            fun decrement(event: MeteringEvent.Decrement): UpDownCounter =
                UpDownCounter(name, tags, kind, unit, description, value.promotingMinus(event.value), event.timestamp)
        }

        /**
         * A gauge that tracks the latest recorded value (it can arbitrarily rise and fall): each
         * observation produces a new aggregate carrying the latest value.
         *
         * - OpenTelemetry: https://opentelemetry.io/docs/specs/otel/metrics/api/#gauge
         * - Prometheus: https://prometheus.io/docs/concepts/metric_types/#gauge
         */
        class Gauge(
            override val name: String,
            override val tags: Tags?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override val value: Number,
            override val updatedAt: Instant? = null,
        ) : Instrument {
            /** Returns a copy carrying the latest observation. */
            fun record(event: MeteringEvent.Record): Gauge =
                Gauge(name, tags, kind, unit, description, event.value, event.timestamp)
        }

        /**
         * Aggregates sampled observations into a running `count` and `sum`. Unlike a gauge (which
         * keeps only the last value), a histogram accumulates every recorded value. Because the
         * aggregate is immutable, a rendered snapshot can never observe a `count` without its
         * matching `sum`.
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
            val count: Long = 0,
            val sum: Double = 0.0,
            override val updatedAt: Instant? = null,
        ) : Instrument {
            // Keep the interface's `value` in sync with the observation count.
            override val value: Number get() = count

            /** Returns a copy with the observation folded into the running `count`/`sum`. */
            fun record(event: MeteringEvent.Record): Histogram =
                Histogram(name, tags, kind, unit, description, count + 1, sum + event.value.toDouble(), event.timestamp)

            /**
             * A histogram is exposed as its cumulative `+Inf` bucket plus the mandatory `_sum` and
             * `_count` series.
             * https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#histogram
             */
            override fun openMetricsValueString(family: String): String = buildString {
                // `le` is reserved for the bucket boundary: a user tag with that (sanitized) name
                // is dropped so the bucket's own `le` never clashes and the label sets stay
                // consistent across the `_bucket`, `_sum` and `_count` series.
                val seriesTags = tags?.filterKeys { it.sanitizeLabelName() != "le" }
                val tagStr = seriesTags?.format() ?: ""
                val bucketTags = ((seriesTags ?: emptyMap()) + ("le" to "+Inf")).format()
                append(family).append("_bucket").append(bucketTags).append(" ").append(count).appendLine()
                append(family).append("_sum").append(tagStr).append(" ").append(sum).appendLine()
                append(family).append("_count").append(tagStr).append(" ").append(count).appendLine()
            }
        }
    }

    companion object {
        // The exposition format restricts metric names to `[a-zA-Z_:][a-zA-Z0-9_:]*` and label names to
        // `[a-zA-Z_][a-zA-Z0-9_]*`; help texts and label values need `\`, `"` and line-feed escaping.
        // https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#abnf

        /** Maps every character outside the exposition-format name alphabet to `_`. */
        private fun String.sanitizeNameChars(allowColon: Boolean = true): String = buildString(length) {
            for (c in this@sanitizeNameChars) {
                val valid = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || (allowColon && c == ':')
                append(if (valid) c else '_')
            }
        }

        /** Sanitizes a metric name: invalid characters become `_`, and a leading digit is prefixed with `_`. */
        private fun String.sanitizeMetricName(): String = sanitizeNameChars().let {
            when {
                it.isEmpty() -> "_"
                it.first() in '0'..'9' -> "_$it"
                else -> it
            }
        }

        /** Sanitizes a label name: invalid characters become `_`, and a leading digit is prefixed with `_`. */
        private fun String.sanitizeLabelName(): String = sanitizeNameChars(allowColon = false).let {
            when {
                it.isEmpty() -> "_"
                it.first() in '0'..'9' -> "_$it"
                else -> it
            }
        }

        /** Escapes a `# HELP` text: `\` becomes `\\` and a line feed becomes `\n`. */
        private fun String.escapeHelpText(): String = replace("\\", "\\\\").replace("\n", "\\n")

        /** Escapes a label value: `\` becomes `\\`, `"` becomes `\"` and a line feed becomes `\n`. */
        private fun String.escapeLabelValue(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        /** `true` for the integral types (`Byte`, `Short`, `Int`, `Long`). */
        private val Number.isIntegral: Boolean
            get() = this is Long || this is Int || this is Short || this is Byte

        /**
         * Adds [other] to this number, promoting the result to the wider of the two numeric types
         * (`Double` > `Float` > `Long`). Integral accumulation is always widened to `Long`, so an
         * `Int`-fed series does not silently wrap at 2^31. (A `Long` accumulator still overflows at
         * 2^63, which is not guarded.) Unknown [Number] implementations fold via `Double`.
         */
        private fun Number.promotingPlus(other: Number): Number = when {
            this is Double || other is Double -> toDouble() + other.toDouble()
            this is Float || other is Float -> toFloat() + other.toFloat()
            isIntegral && other.isIntegral -> toLong() + other.toLong()
            else -> toDouble() + other.toDouble()
        }

        /** Subtracts [other] from this number; same promotion rules as [promotingPlus]. */
        private fun Number.promotingMinus(other: Number): Number = when {
            this is Double || other is Double -> toDouble() - other.toDouble()
            this is Float || other is Float -> toFloat() - other.toFloat()
            isIntegral && other.isIntegral -> toLong() - other.toLong()
            else -> toDouble() - other.toDouble()
        }
    }
}
