package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.Meter.Instrument.Kind
import io.github.smyrgeorge.log4k.impl.Tags
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Sealed interface for metering events used within a telemetry or monitoring system.
 * All events have a unique identifier, a name, and a timestamp. This interface can
 * be extended by different types of metering events.
 */
sealed interface MeteringEvent {
    val id: Long
    val name: String
    val timestamp: Instant

    /**
     * Registers an instrument's metadata with the collectors.
     *
     * [boundaries] is histogram-only: the explicit bucket upper bounds set at the definition site
     * via `Meter.histogram(boundaries = …)`, defaulting to
     * [Meter.Companion.DEFAULT_HISTOGRAM_BUCKET_BOUNDARIES]. It is empty for every other
     * instrument kind — and for a histogram that wants no finite buckets (`count`/`sum` only).
     */
    data class CreateInstrument(
        override val id: Long,
        override val name: String,
        val kind: Kind,
        val unit: String?,
        val description: String?,
        val boundaries: List<Double> = emptyList(),
        override val timestamp: Instant = Clock.System.now(),
    ) : MeteringEvent

    sealed interface ValueEvent : MeteringEvent {
        val tags: Tags
        val value: Number
    }

    data class Set(
        override val id: Long,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Increment(
        override val id: Long,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Decrement(
        override val id: Long,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Record(
        override val id: Long,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent
}
