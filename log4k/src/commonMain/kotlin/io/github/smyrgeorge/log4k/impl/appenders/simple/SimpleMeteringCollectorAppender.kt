package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.datetime.Instant

class SimpleMeteringCollectorAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()
    private val registry: MutableMap<Int, Instrument> = mutableMapOf()
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
            .groupBy { it.name }
            .forEach { group ->
                val instruments = group.value
                val first = instruments.first()
                append(first.openMetricsHeaderString())
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
        val existing = registry[key()]
        return if (existing == null) {
            val info = instruments[name] ?: return null
            when (info.kind) {
                Meter.Instrument.Kind.Counter -> Instrument.Counter(
                    name = name,
                    labels = labels,
                    kind = info.kind,
                    unit = info.unit,
                    description = info.description,
                    value = 0,
                    updatedAt = timestamp
                )

                Meter.Instrument.Kind.UpDownCounter -> Instrument.UpDownCounter(
                    name = name,
                    labels = labels,
                    kind = info.kind,
                    unit = info.unit,
                    description = info.description,
                    value = 0,
                    updatedAt = timestamp
                )

                Meter.Instrument.Kind.Gauge -> Instrument.Gauge(
                    name = name,
                    labels = labels,
                    kind = info.kind,
                    unit = info.unit,
                    description = info.description,
                    value = 0,
                    updatedAt = timestamp
                )

                Meter.Instrument.Kind.Histogram -> TODO()
            }.also { registry[key()] = it }
        } else existing
    }

    sealed interface Instrument {
        val name: String
        val kind: Meter.Instrument.Kind
        val unit: String?
        val description: String?
        val labels: Map<String, Any>?
        var value: Number
        var updatedAt: Instant?

        fun sortKey(): Int = labels.hashCode()

        fun openMetricsHeaderString(): String = buildString {
            append("# HELP ").append(name).append(" ")
            description?.let { d ->
                append(d)
                unit?.let { append(" (").append(it).append(")") }
            }
            appendLine()
            append("# TYPE ").append(name).append(" ").append(kind.name.lowercase()).appendLine()
        }

        fun openMetricsValueString(): String = buildString {
            append(name).append(" ")
            labels?.let { append(it.format()).append(" ") }
            append(value)
            updatedAt?.let { append(" ").append(it.toEpochMilliseconds()) }
            appendLine()
        }

        fun Map<String, Any>.format(): String =
            entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=\"$v\"" }

        data class Info(
            val name: String,
            val kind: Meter.Instrument.Kind,
            val unit: String?,
            val description: String?,
        )

        abstract class AbstractCounter(
            override val name: String,
            override val labels: Map<String, Any>?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override var value: Number,
            override var updatedAt: Instant? = null,
        ) : Instrument {
            fun increment(event: MeteringEvent.Increment) {
                updatedAt = event.timestamp
                when (event.value) {
                    is Int -> value = value.toInt() + event.value.toInt()
                    is Long -> value = value.toLong() + event.value.toLong()
                    is Float -> value = value.toFloat() + event.value.toFloat()
                    is Double -> value = value.toDouble() + event.value.toDouble()
                }
            }
        }

        abstract class AbstractRecorder(
            override val name: String,
            override val labels: Map<String, Any>?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            override var value: Number,
            override var updatedAt: Instant? = null,
        ) : Instrument {
            fun record(event: MeteringEvent.Record) {
                updatedAt = event.timestamp
                value = event.value
            }
        }

        class Counter(
            name: String,
            labels: Map<String, Any>?,
            kind: Meter.Instrument.Kind,
            unit: String?,
            description: String?,
            value: Number,
            updatedAt: Instant? = null,
        ) : AbstractCounter(name, labels, kind, unit, description, value, updatedAt)

        class UpDownCounter(
            name: String,
            labels: Map<String, Any>?,
            kind: Meter.Instrument.Kind,
            unit: String?,
            description: String?,
            value: Number,
            updatedAt: Instant? = null,
        ) : AbstractCounter(name, labels, kind, unit, description, value, updatedAt) {
            fun decrement(event: MeteringEvent.Decrement) {
                if (kind == Meter.Instrument.Kind.Counter) return
                updatedAt = event.timestamp
                when (event.value) {
                    is Int -> value = value.toInt() - event.value.toInt()
                    is Long -> value = value.toLong() - event.value.toLong()
                    is Float -> value = value.toFloat() - event.value.toFloat()
                    is Double -> value = value.toDouble() - event.value.toDouble()
                }
            }
        }

        class Gauge(
            name: String,
            labels: Map<String, Any>?,
            kind: Meter.Instrument.Kind,
            unit: String?,
            description: String?,
            value: Number,
            updatedAt: Instant? = null,
        ) : AbstractRecorder(name, labels, kind, unit, description, value, updatedAt)
    }
}