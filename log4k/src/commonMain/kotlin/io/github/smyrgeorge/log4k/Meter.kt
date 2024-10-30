package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
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
        unit: String? = null,
        description: String? = null
    ): Instrument.Counter<T> = Instrument.Counter(name, this, unit, description)

    fun <T : Number> upDownCounter(
        name: String,
        unit: String? = null,
        description: String? = null
    ): Instrument.UpDownCounter<T> = Instrument.UpDownCounter(name, this, unit, description)

    fun <T : Number> gauge(
        name: String,
        unit: String? = null,
        description: String? = null
    ): Instrument.Gauge<T> = Instrument.Gauge(name, this, unit, description)

    // https://opentelemetry.io/docs/specs/otel/metrics/api/#meter
    @Suppress("MemberVisibilityCanBePrivate")
    sealed class Instrument(
        val name: String,
        val meter: Meter,
        val kind: Kind,
        val unit: String? = null,
        val description: String? = null,
    ) {
        init {
            init()
        }

        private fun init() {
            if (meter.isMuted()) return
            MeteringEvent.CreateInstrument(
                name = name,
                kind = kind,
                unit = unit,
                description = description
            ).also { RootLogger.meter(it) }
        }

        enum class Kind {
            Counter,
            UpDownCounter,
            Gauge,
            Histogram,
        }

        sealed class AbstractCounter<T : Number>(
            name: String,
            meter: Meter,
            kind: Kind,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, meter, kind, unit, description) {
            fun increment(value: T, vararg labels: Pair<String, Any>) {
                if (meter.isMuted()) return
                if (value.isLessThanZero()) error("Only non-negative values are allowed.")
                val event = MeteringEvent.Increment(name = name, labels = labels.toMap(), value = value)
                RootLogger.meter(event)
            }

            internal fun Number.isLessThanZero(): Boolean = when (this) {
                is Int -> this < 0
                is Long -> this < 0
                is Float -> this < 0
                is Double -> this < 0
                else -> error("Unsupported number type: ${this::class.toName()}")
            }
        }

        sealed class AbstractRecorder<T : Number>(
            name: String,
            meter: Meter,
            kind: Kind,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, meter, kind, unit, description) {
            fun record(value: T, vararg labels: Pair<String, Any>) {
                if (meter.isMuted()) return
                val event = MeteringEvent.Record(name = name, labels = labels.toMap(), value = value)
                RootLogger.meter(event)
            }
        }

        class Counter<T : Number>(
            name: String,
            meter: Meter,
            unit: String?,
            description: String?,
        ) : AbstractCounter<T>(name, meter, Kind.Counter, unit, description)

        class UpDownCounter<T : Number>(
            name: String,
            meter: Meter,
            unit: String?,
            description: String?,
        ) : AbstractCounter<T>(name, meter, Kind.UpDownCounter, unit, description) {
            fun decrement(value: T, vararg labels: Pair<String, Any>) {
                if (meter.isMuted()) return
                if (value.isLessThanZero()) error("Only non-negative values are allowed.")
                val event = MeteringEvent.Increment(name = name, labels = labels.toMap(), value = value)
                RootLogger.meter(event)
            }
        }

        class Gauge<T : Number>(
            name: String,
            meter: Meter,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, meter, Kind.Gauge, unit, description)
    }

    companion object {
        fun of(name: String): Meter = RootLogger.Metering.factory.get(name)
        fun of(clazz: KClass<*>): Meter = RootLogger.Metering.factory.get(clazz)
        inline fun <reified T : Meter> ofType(name: String): T = of(name) as T
        inline fun <reified T : Meter> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}