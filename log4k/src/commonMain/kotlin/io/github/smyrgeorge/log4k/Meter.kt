package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.dispatcher
import io.github.smyrgeorge.log4k.impl.extensions.toName
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * The `Meter` class serves as an abstract base class for creating different types of metering instruments
 * such as Counters, UpDownCounters, and Gauges. It extends `CollectorRegistry.Collector` and provides
 * specialized methods to create these instruments with specified names, units of measurement,
 * and optional descriptions.
 *
 * @constructor Creates an instance of `Meter` with the specified name and logging level.
 *
 * @param name The name of the meter.
 * @param level The logging level for the meter.
 */
@Suppress("unused")
abstract class Meter(
    final override val name: String,
    final override var level: Level
) : CollectorRegistry.Collector {
    override var levelBeforeMute: Level = level

    /**
     * Creates a new counter instrument with the specified name, unit, and description.
     *
     * @param T the numeric type of the counter.
     * @param name the name of the counter.
     * @param unit the unit of measurement for the counter, which is optional.
     * @param description a description of the counter, which is optional.
     * @return a new `Instrument.Counter` instance.
     */
    fun <T : Number> counter(
        name: String,
        unit: String? = null,
        description: String? = null
    ): Instrument.Counter<T> = Instrument.Counter(name, this, unit, description)

    /**
     * Creates a new up-down counter instrument with the specified name, unit, and description.
     *
     * @param T the numeric type of the counter.
     * @param name the name of the counter.
     * @param unit the unit of measurement for the counter, which is optional.
     * @param description a description of the counter, which is optional.
     * @return a new `Instrument.UpDownCounter` instance.
     */
    fun <T : Number> upDownCounter(
        name: String,
        unit: String? = null,
        description: String? = null
    ): Instrument.UpDownCounter<T> = Instrument.UpDownCounter(name, this, unit, description)

    /**
     * Creates a new gauge instrument with the specified name, unit, and description.
     *
     * @param T the numeric type of the gauge.
     * @param name the name of the gauge.
     * @param unit the unit of measurement for the gauge, which is optional.
     * @param description a description of the gauge, which is optional.
     * @return a new `Instrument.Gauge` instance.
     */
    fun <T : Number> gauge(
        name: String,
        unit: String? = null,
        description: String? = null
    ): Instrument.Gauge<T> = Instrument.Gauge(name, this, unit, description)

    /**
     * Represents a base instrument used for recording various types of metric data.
     * https://opentelemetry.io/docs/specs/otel/metrics/api/#meter
     *
     * @param name the name of the instrument.
     * @param meter the Meter instance to which this instrument belongs.
     * @param kind the kind of the instrument, defined by the `Kind` enum.
     * @param unit the unit of measurement for the instrument, which is optional.
     * @param description a description of the instrument, which is optional.
     */
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

            fun poll(every: Duration, f: (AbstractRecorder<T>) -> Unit) {
                GaugeScope.launch(dispatcher) {
                    runCatching {
                        while (true) {
                            delay(every)
                            f(this@AbstractRecorder)
                        }
                    }
                }
            }

            companion object {
                private val dispatcher: CoroutineDispatcher = dispatcher()

                private object GaugeScope : CoroutineScope {
                    override val coroutineContext: CoroutineContext
                        get() = EmptyCoroutineContext
                }
            }
        }

        /**
         * Represents a Counter instrument used for recording numerical values
         * that can only increase. This class is a specialized type of AbstractCounter
         * utilized within a Metering system.
         *
         * @param T the numeric type of the counter.
         * @param name the name of the counter.
         * @param meter the Meter instance to which this counter belongs.
         * @param unit the unit of measurement for the counter, which is optional.
         * @param description a description of the counter, which is optional.
         */
        class Counter<T : Number>(
            name: String,
            meter: Meter,
            unit: String?,
            description: String?,
        ) : AbstractCounter<T>(name, meter, Kind.Counter, unit, description)

        /**
         * Represents an UpDownCounter instrument used for recording numerical values
         * that can increase or decrease. This class is a specialized type of
         * AbstractCounter used within a Metering system.
         *
         * @param T the numeric type of the counter.
         * @param name the name of the counter.
         * @param meter the Meter instance to which this counter belongs.
         * @param unit the unit of measurement for the counter, which is optional.
         * @param description a description of the counter, which is optional.
         */
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

        /**
         * Represents a gauge instrument for measuring numerical values that can
         * arbitrarily go up and down.
         *
         * @param T the numeric type of the gauge.
         * @param name the name of the gauge.
         * @param meter the Meter instance to which this gauge belongs.
         * @param unit the unit of measurement for the gauge, which is optional.
         * @param description a description of the gauge, which is optional.
         */
        class Gauge<T : Number>(
            name: String,
            meter: Meter,
            unit: String? = null,
            description: String? = null,
        ) : AbstractRecorder<T>(name, meter, Kind.Gauge, unit, description)
    }

    companion object {
        fun of(name: String): Meter = RootLogger.Metering.factory.get(name)
        fun of(clazz: KClass<*>): Meter = RootLogger.Metering.factory.get(clazz)
        inline fun <reified T : Meter> ofType(name: String): T = of(name) as T
        inline fun <reified T : Meter> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}