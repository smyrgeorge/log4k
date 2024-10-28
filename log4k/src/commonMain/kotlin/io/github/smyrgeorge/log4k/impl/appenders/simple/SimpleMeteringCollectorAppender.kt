package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.datetime.Instant

class SimpleMeteringCollectorAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()
    private val registry: MutableMap<Int, Instrument> = mutableMapOf()

    override suspend fun append(event: MeteringEvent) {
        when (event) {
            is MeteringEvent.CreateCountingInstrument -> {
                val key = event.key()
                if (registry.containsKey(key)) return
                Instrument.Counting(
                    name = event.name,
                    attributes = event.attributes,
                    createdAt = event.timestamp,
                    kind = event.kind,
                    unit = event.unit,
                    description = event.description,
                    value = event.initial,
                ).also { registry[key] = it }
            }

            is MeteringEvent.Add -> {
                when (val holder = registry[event.key()]) {
                    is Instrument.Counting -> {
                        holder.add(event.timestamp, event.value)
                    }

                    else -> Unit
                }
            }
        }
    }

    fun prometheusString(): String = buildString {
        registry
            .values
            .groupBy { it.name }
            .forEach { group ->
                val instruments = group.value
                val first = instruments.first()
                append(first.prometheusHeaderString())
                instruments.forEach {
                    append(it.prometheusValueString())
                }
            }
    }

    sealed interface Instrument {
        val name: String
        val attributes: Map<String, Any>?

        fun prometheusHeaderString(): String
        fun prometheusValueString(): String

        @Suppress("unused", "MemberVisibilityCanBePrivate")
        class Counting(
            override val name: String,
            override val attributes: Map<String, Any>?,
            val createdAt: Instant,
            val kind: Meter.Instrument.Kind,
            val unit: String?,
            val description: String?,
            var value: Number,
            var updatedAt: Instant? = null,
        ) : Instrument {
            fun add(timestamp: Instant, v: Number) {
                updatedAt = timestamp
                when (value) {
                    is Int -> value = value.toInt() + v.toInt()
                    is Long -> value = value.toLong() + v.toLong()
                    is Float -> value = value.toFloat() + v.toFloat()
                    is Double -> value = value.toDouble() + v.toDouble()
                }
            }

            override fun prometheusHeaderString(): String = buildString {
                append("# HELP ")
                append(name)
                append(" ")
                description?.let { d ->
                    append(d)
                    unit?.let {
                        append(" in ")
                        append(it)
                    }
                }
                appendLine()

                append("# TYPE ")
                append(name)
                append(" ")
                append(kind.name.lowercase())
                appendLine()
            }

            override fun prometheusValueString(): String = buildString {
                append(name)
                attributes?.let {
                    append(attributes.toString())
                }
                append(" ")
                append(value)
                appendLine()
            }
        }
    }
}