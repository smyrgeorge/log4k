package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
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

    fun <T : Number> counter(
        name: String,
        initial: T,
        attributes: Map<String, Any>? = null,
        unit: String? = null,
        description: String? = null
    ): Instrument.Counter<T> = Instrument.Counter(name, this, attributes, initial, unit, description)

    inline fun <T : Number> counter(
        name: String,
        initial: T,
        unit: String? = null,
        description: String? = null,
        f: (MutableMap<String, Any>) -> Unit
    ): Instrument.Counter<T> {
        val attributes = mutableMapOf<String, Any>()
        f(attributes)
        return Instrument.Counter(name, this, attributes, initial, unit, description)
    }

    fun <T : Number> upDownCounter(
        name: String,
        initial: T,
        attributes: Map<String, Any>? = null,
        unit: String? = null,
        description: String? = null
    ): Instrument.UpDownCounter<T> = Instrument.UpDownCounter(name, this, attributes, initial, unit, description)

    inline fun <T : Number> upDownCounter(
        name: String,
        initial: T,
        unit: String? = null,
        description: String? = null,
        f: (MutableMap<String, Any>) -> Unit
    ): Instrument.UpDownCounter<T> {
        val attributes = mutableMapOf<String, Any>()
        f(attributes)
        return Instrument.UpDownCounter(name, this, attributes, initial, unit, description)
    }

    // https://opentelemetry.io/docs/specs/otel/metrics/api/#meter
    sealed class Instrument(
        val name: String,
        val meter: Meter,
        val attributes: Map<String, Any>?,
        val kind: Kind,
        val unit: String? = null,
        val description: String? = null,
    ) {

        enum class Kind {
            Counter,
            Histogram,
            Gauge,
            UpDownCounter,
        }

        sealed class Counting(
            name: String,
            meter: Meter,
            attributes: Map<String, Any>?,
            kind: Kind,
            initial: Number,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, meter, attributes, kind, unit, description) {
            init {
                if (!meter.isMuted()) {
                    val event = MeteringEvent.CreateCountingInstrument(
                        name = name,
                        attributes = attributes,
                        kind = kind,
                        initial = initial,
                        unit = unit,
                        description = description
                    )
                    RootLogger.meter(event)
                }
            }

            fun add(value: Number) {
                if (meter.isMuted()) return
                val event = MeteringEvent.Add(
                    name = name,
                    attributes = attributes,
                    value = value
                )
                RootLogger.meter(event)
            }
        }

        class Counter<T : Number>(
            name: String,
            meter: Meter,
            attributes: Map<String, Any>?,
            initial: T,
            unit: String? = null,
            description: String? = null,
        ) : Counting(name, meter, attributes, Kind.Counter, initial, unit, description)

        class UpDownCounter<T : Number>(
            name: String,
            meter: Meter,
            attributes: Map<String, Any>?,
            initial: T,
            unit: String? = null,
            description: String? = null,
        ) : Counting(name, meter, attributes, Kind.UpDownCounter, initial, unit, description)

//        class Histogram<T>(
//            name: String,
//            group: String,
//            unit: String? = null,
//            description: String? = null,
//        ) : Instrument(name, group, Kind.Histogram, unit, description) where T : Number {
//            private var value: T? = null
//            private var attributes: Map<String, Any?>? = null
//
//            fun record(v: T, attrs: Map<String, Any?> = emptyMap()) {
//                value = v
//                attributes = attrs
//                meter()
//            }
//
//            inline fun record(v: T, f: (MutableMap<String, Any?>) -> Unit) {
//                mutableMapOf<String, Any?>().also {
//                    f(it)
//                    record(v, it)
//                }
//            }
//        }
//
//        class Gauge<T>(
//            name: String,
//            group: String,
//            unit: String? = null,
//            initial: T,
//            description: String? = null,
//        ) : Instrument(name, group, Kind.Gauge, unit, description) where T : Number {
//            private var value: T = initial
//            private var attributes: Map<String, Any?>? = null
//
//            fun record(v: T, attrs: Map<String, Any?> = emptyMap()) {
//                value = v
//                attributes = attrs
//                meter()
//            }
//
//            inline fun record(value: T, f: (MutableMap<String, Any?>) -> Unit) {
//                mutableMapOf<String, Any?>().also {
//                    f(it)
//                    record(value, it)
//                }
//            }
//        }
    }

    companion object {
        fun of(name: String): Meter = RootLogger.Metering.factory.get(name)
        fun of(clazz: KClass<*>): Meter = RootLogger.Metering.factory.get(clazz)
        inline fun <reified T : Meter> ofType(name: String): T = of(name) as T
        inline fun <reified T : Meter> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}