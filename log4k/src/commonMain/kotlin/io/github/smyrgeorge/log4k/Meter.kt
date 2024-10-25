package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.reflect.KClass

abstract class Meter(
    final override val name: String,
    final override var level: Level
) : LoggerRegistry.Collector {
    private var levelBeforeMute: Level = level

    override fun mute() {
        levelBeforeMute = level
        level = Level.OFF
    }

    override fun unmute() {
        level = levelBeforeMute
        levelBeforeMute = level
    }

    companion object {
        fun of(name: String): Meter = RootLogger.Metering.factory.get(name)
        fun of(clazz: KClass<*>): Meter = RootLogger.Metering.factory.get(clazz)
    }

    // https://opentelemetry.io/docs/specs/otel/metrics/api/#meter
    @Suppress("DuplicatedCode")
    abstract class Instrument(
        val name: String,
        val kind: Kind,
        val unit: String? = null,
        val description: String? = null,
    ) {
        enum class Kind(
            // https://opentelemetry.io/docs/specs/otel/metrics/api/#meter
            val async: Boolean
        ) {
            Counter(false),
            AsyncCounter(true),
            Histogram(false),
            Gauge(false),
            AsyncGauge(true),
            UpDownCounter(false),
            AsyncUpDownCounter(true),
        }

        class Counter<T>(
            name: String,
            initial: T,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, Kind.Counter, unit, description) where T : Number {
            private var value: T = initial

            init {
                when (value) {
                    is Long, is Double -> Unit
                    else -> error("Unsupported type ${value::class.simpleName}.")
                }
            }

            fun add(v: T) {
                @Suppress("UNCHECKED_CAST")
                value = when (v) {
                    is Long -> (value.toLong() + v) as T
                    is Double -> (value.toDouble() + v) as T
                    else -> error("Unsupported type ${v::class.simpleName}.")
                }
            }
        }

        class Histogram<T>(
            name: String,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, Kind.Histogram, unit, description) where T : Number {
            private var value: T? = null
            private var attributes: Map<String, Any?>? = null

            fun record(v: T, attrs: Map<String, Any?> = emptyMap()) {
                value = v
                attributes = attrs
            }

            inline fun record(v: T, f: (MutableMap<String, Any?>) -> Unit) {
                mutableMapOf<String, Any?>().also {
                    f(it)
                    record(v, it)
                }
            }
        }

        class Gauge<T>(
            name: String,
            unit: String? = null,
            initial: T,
            description: String? = null,
        ) : Instrument(name, Kind.Gauge, unit, description) where T : Number {
            private var value: T = initial
            private var attributes: Map<String, Any?>? = null

            fun record(v: T, attrs: Map<String, Any?> = emptyMap()) {
                value = v
                attributes = attrs
            }

            inline fun record(value: T, f: (MutableMap<String, Any?>) -> Unit) {
                mutableMapOf<String, Any?>().also {
                    f(it)
                    record(value, it)
                }
            }
        }

        class UpDownCounter<T>(
            name: String,
            initial: T,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, Kind.UpDownCounter, unit, description) where T : Number {
            private var value: T = initial

            init {
                when (value) {
                    is Long, is Double -> Unit
                    else -> error("Unsupported type ${value::class.simpleName}.")
                }
            }

            fun add(v: T) {
                @Suppress("UNCHECKED_CAST")
                value = when (v) {
                    is Long -> (value.toLong() + v) as T
                    is Double -> (value.toDouble() + v) as T
                    else -> error("Unsupported type ${v::class.simpleName}.")
                }
            }
        }
    }
}