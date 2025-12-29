@file:Suppress("unused")

package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.SimpleMeterFactory
import io.github.smyrgeorge.log4k.impl.extensions.dispatcher
import io.github.smyrgeorge.log4k.impl.extensions.doEvery
import io.github.smyrgeorge.log4k.impl.extensions.toName
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
abstract class Meter(
    final override val name: String,
    final override var level: Level
) : CollectorRegistry.Collector {
    override var levelBeforeMute: Level = level

    /**
     * Creates a new counter-instrument with the specified name, unit, and description.
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

        /**
         * Initializes the instrument by creating a metering event if the meter is not muted.
         * This method checks if the meter is muted and, if not, creates a `CreateInstrument`
         * metering event with the instrument's details, then logs the event using the RootLogger.
         */
        private fun init() {
            if (meter.isMuted()) return
            MeteringEvent.CreateInstrument(
                name = name,
                kind = kind,
                unit = unit,
                description = description
            ).also { RootLogger.meter(it) }
        }

        /**
         * Enum class representing the types of instruments that can be used within the metering system.
         *
         * - `Counter`: Used for recording numerical values that can only increase.
         * - `UpDownCounter`: Used for recording numerical values that can both increase and decrease.
         * - `Gauge`: Used for measuring numerical values that can arbitrarily go up and down.
         * - `Histogram`: Used for recording distributions of numerical values.
         */
        enum class Kind {
            Counter,
            UpDownCounter,
            Gauge,
            Histogram,
        }

        /**
         * AbstractCounter is a sealed class that extends the Instrument class.
         * It represents a generic counter-instrument which can increment its value
         * based on the provided numerical input of type T. T must extend the Number class.
         *
         * @param name Name of the counter.
         * @param meter Meter instance associated with this counter.
         * @param kind Kind of instrument.
         * @param unit Optional unit of measurement.
         * @param description Optional description of the counter.
         */
        sealed class AbstractCounter<T : Number>(
            name: String,
            meter: Meter,
            kind: Kind,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, meter, kind, unit, description) {
            /**
             * Sets the counter to the specified non-negative value with associated tags.
             *
             * @param value The non-negative value to set the counter to.
             * @param tags A vararg of pairs representing additional tags/metadata associated with the set action.
             */
            fun set(value: T, vararg tags: Pair<String, Any>) {
                if (meter.isMuted()) return
                if (value.isLessThanZero()) error("Only non-negative values are allowed.")
                val event = MeteringEvent.Set(name = name, tags = tags.toMap(), value = value)
                RootLogger.meter(event)
            }

            /**
             * Increments the counter by the specified non-negative value with associated tags.
             *
             * @param value The non-negative value by which to increment the counter.
             * @param tags A vararg of pairs representing additional tags/metadata associated with the increment action.
             */
            fun increment(value: T, vararg tags: Pair<String, Any>) {
                if (meter.isMuted()) return
                if (value.isLessThanZero()) error("Only non-negative values are allowed.")
                val event = MeteringEvent.Increment(name = name, tags = tags.toMap(), value = value)
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

        /**
         * AbstractRecorder is a sealed class used for recording numerical data attached to specific tags.
         * It extends the Instrument class and provides functionality for recording and periodically polling data.
         *
         * @param T the type of numerical data to be recorded, constrained by the Number class.
         * @param name the name of the recorder.
         * @param meter the Meter instance associated with this recorder.
         * @param kind the type of the instrument.
         * @param unit the unit of measurement, default is null.
         * @param description the description of the recorder, default is null.
         */
        sealed class AbstractRecorder<T : Number>(
            name: String,
            meter: Meter,
            kind: Kind,
            unit: String? = null,
            description: String? = null,
        ) : Instrument(name, meter, kind, unit, description) {
            /**
             * Records a metering event with a specified value and optional tags.
             * If the meter is muted, the event will not be recorded.
             *
             * @param value The value to be recorded.
             * @param tags A variable number of pairs representing the tags associated with the event.
             */
            fun record(value: T, vararg tags: Pair<String, Any>) {
                if (meter.isMuted()) return
                val event = MeteringEvent.Record(name = name, tags = tags.toMap(), value = value)
                RootLogger.meter(event)
            }

            /**
             * Schedules a polling task that runs a given suspending function at a specified interval.
             *
             * @param every The duration between consecutive executions of the function.
             * @param initial The initial delay before the first execution of the function. Defaults to 10 seconds.
             * @param f The suspending function to be executed at each polling interval.
             */
            fun poll(every: Duration, initial: Duration = 10.seconds, f: suspend AbstractRecorder<T>.() -> Unit) {
                doEvery(every, dispatcher) {
                    f(this@AbstractRecorder)
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
         * used within a Metering system.
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
            /**
             * Decrements the value of an UpDownCounter instrument and logs the event if the meter is not muted.
             * The value must be non-negative.
             *
             * @param value The non-negative value to decrement from the counter.
             * @param tags Optional key-value pairs of tags associated with the decrement event.
             */
            fun decrement(value: T, vararg tags: Pair<String, Any>) {
                if (meter.isMuted()) return
                if (value.isLessThanZero()) error("Only non-negative values are allowed.")
                val event = MeteringEvent.Increment(name = name, tags = tags.toMap(), value = value)
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
        val registry = CollectorRegistry<Meter>()
        var factory: MeterFactory = SimpleMeterFactory()
        fun of(name: String): Meter = factory.get(name)
        fun of(clazz: KClass<*>): Meter = factory.get(clazz)
        inline fun <reified T : Meter> ofType(name: String): T = factory.get(name) as T
        inline fun <reified T : Meter> ofType(clazz: KClass<*>): T = factory.get(clazz) as T
    }
}
