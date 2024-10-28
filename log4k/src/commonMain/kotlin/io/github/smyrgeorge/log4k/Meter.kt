package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlinx.datetime.Clock
import kotlin.reflect.KClass

@Suppress("unused")
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
        inline fun <reified T : Meter> ofType(name: String): T = of(name) as T
        inline fun <reified T : Meter> ofType(clazz: KClass<*>): T = of(clazz) as T
    }

    // https://opentelemetry.io/docs/specs/otel/metrics/api/#meter
    abstract class Instrument(
        val name: String,
        val group: String,
        val kind: Kind,
        val unit: String? = null,
        val description: String? = null,
    ) {

        protected fun meter() {
            val event = MeteringEvent.Measurement(timestamp = Clock.System.now(), instrument = this)
            RootLogger.meter(event)
        }

        enum class Kind {
            Counter,
            Histogram,
            Gauge,
            UpDownCounter,
        }

        class Counter<T>(
            name: String,
            group: String,
            initial: T,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, group, Kind.Counter, unit, description) where T : Number {
            private var value: T = initial

            init {
                when (value) {
                    is Long, is Double -> Unit
                    else -> error("Unsupported type ${value::class.toName()}.")
                }
            }

            fun add(v: T) {
                @Suppress("UNCHECKED_CAST")
                value = when (v) {
                    is Long -> (value.toLong() + v) as T
                    is Double -> (value.toDouble() + v) as T
                    else -> error("Unsupported type ${v::class.toName()}.")
                }
                meter()
            }
        }

        class Histogram<T>(
            name: String,
            group: String,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, group, Kind.Histogram, unit, description) where T : Number {
            private var value: T? = null
            private var attributes: Map<String, Any?>? = null

            fun record(v: T, attrs: Map<String, Any?> = emptyMap()) {
                value = v
                attributes = attrs
                meter()
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
            group: String,
            unit: String? = null,
            initial: T,
            description: String? = null,
        ) : Instrument(name, group, Kind.Gauge, unit, description) where T : Number {
            private var value: T = initial
            private var attributes: Map<String, Any?>? = null

            fun record(v: T, attrs: Map<String, Any?> = emptyMap()) {
                value = v
                attributes = attrs
                meter()
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
            group: String,
            initial: T,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, group, Kind.UpDownCounter, unit, description) where T : Number {
            private var value: T = initial

            init {
                when (value) {
                    is Long, is Double -> Unit
                    else -> error("Unsupported type ${value::class.toName()}.")
                }
            }

            fun add(v: T) {
                @Suppress("UNCHECKED_CAST")
                value = when (v) {
                    is Long -> (value.toLong() + v) as T
                    is Double -> (value.toDouble() + v) as T
                    else -> error("Unsupported type ${v::class.toName()}.")
                }
                meter()
            }
        }
    }
}