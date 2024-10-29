package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.datetime.Instant

class SimpleMeteringCollectorAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()
    private val registry: MutableMap<Int, Instrument> = mutableMapOf()
    private val counters: MutableMap<String, Instrument.AbstractCounter.Info> = mutableMapOf()

    override suspend fun append(event: MeteringEvent) {
        when (event) {
            is MeteringEvent.CreateCounter -> {
                if (counters.containsKey(event.name)) return
                Instrument.AbstractCounter.Info(event.name, event.kind, event.unit, event.description)
                    .also { counters[event.name] = it }
            }

            is MeteringEvent.CounterOperation -> {
                when (val e: MeteringEvent.CounterOperation = event) {
                    is MeteringEvent.Increment -> {
                        when (val instrument = e.instrument()) {
                            is Instrument.Counter -> instrument.increment(e)
                            is Instrument.UpDownCounter -> instrument.increment(e)
                            else -> Unit
                        }
                    }

                    is MeteringEvent.Decrement -> {
                        when (val instrument = e.instrument()) {
                            is Instrument.UpDownCounter -> instrument.decrement(e)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    /**
     * Instruments a metering event of type CounterOperation to produce an appropriate Instrument.
     *
     * This method checks if an instrument corresponding to the given metering event already exists in the registry.
     * If it does, the existing instrument is returned. Otherwise,
     * the method creates a new instrument based on the metering event details.
     *
     * @return The corresponding Instrument if available or created, otherwise null.
     */
    private fun MeteringEvent.CounterOperation.instrument(): Instrument? {
        val existing = registry[key()]
        return if (existing == null) {
            val info = counters[name] ?: return null
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

                Meter.Instrument.Kind.Histogram -> TODO()
                Meter.Instrument.Kind.Gauge -> TODO()
            }.also { registry[key()] = it }
        } else existing
    }

    /**
     * Generates a string representation of the metrics in a format suitable for Prometheus.
     * https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exposition_formats.md
     *
     * @return A Prometheus-compliant string representation of the collected metrics.
     */
    fun prometheusString(): String = buildString {
        registry
            .values
            .groupBy { it.name }
            .forEach { group ->
                val instruments = group.value
                val first = instruments.first()
                append(first.prometheusHeaderString())
                instruments
                    .sortedBy { it.sortKey() }
                    .forEach {
                        append(it.prometheusValueString())
                    }
            }
    }

    sealed interface Instrument {
        val name: String
        val kind: Meter.Instrument.Kind
        val unit: String?
        val description: String?
        val labels: Map<String, Any>?
        fun sortKey(): Int = labels.hashCode()
        fun prometheusValueString(): String

        fun prometheusHeaderString(): String = buildString {
            append("# HELP ").append(name).append(" ")
            description?.let { d ->
                append(d)
                unit?.let { append(" (").append(it).append(")") }
            }
            appendLine()
            append("# TYPE ").append(name).append(" ").append(kind.name.lowercase()).appendLine()
        }

        fun Map<String, Any>.format(): String =
            entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=\"$v\"" }

        abstract class AbstractCounter(
            override val name: String,
            override val labels: Map<String, Any>?,
            override val kind: Meter.Instrument.Kind,
            override val unit: String?,
            override val description: String?,
            var value: Number,
            var updatedAt: Instant? = null,
        ) : Instrument {
            data class Info(
                val name: String,
                val kind: Meter.Instrument.Kind,
                val unit: String?,
                val description: String?,
            )

            fun increment(event: MeteringEvent.Increment) {
                updatedAt = event.timestamp
                when (event.value) {
                    is Int -> value = value.toInt() + event.value.toInt()
                    is Long -> value = value.toLong() + event.value.toLong()
                    is Float -> value = value.toFloat() + event.value.toFloat()
                    is Double -> value = value.toDouble() + event.value.toDouble()
                }
            }

            override fun prometheusValueString(): String = buildString {
                append(name).append(" ")
                labels?.let { append(it.format()).append(" ") }
                append(value)
                updatedAt?.let { append(" ").append(it.toEpochMilliseconds()) }
                appendLine()
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
    }
}