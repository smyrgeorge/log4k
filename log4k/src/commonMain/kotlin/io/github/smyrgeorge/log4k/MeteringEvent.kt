package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.Meter.Instrument.Kind
import io.github.smyrgeorge.log4k.impl.Tags
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Sealed interface for metering events used within a telemetry or monitoring system.
 * All events have a unique identifier, a name, and a timestamp. This interface can
 * be extended by different types of metering events.
 */
sealed interface MeteringEvent {
    var id: Long
    val name: String
    val timestamp: Instant

    fun key(): Int

    data class CreateInstrument(
        override var id: Long = 0L,
        override val name: String,
        val kind: Kind,
        val unit: String?,
        val description: String?,
        override val timestamp: Instant = Clock.System.now(),
    ) : MeteringEvent {
        override fun key(): Int = name.hashCode()
    }

    sealed interface ValueEvent : MeteringEvent {
        val tags: Tags
        val value: Number
        override fun key(): Int = "$name.${tags.hashCode()}".hashCode()
    }

    data class Set(
        override var id: Long = 0L,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Increment(
        override var id: Long = 0L,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Decrement(
        override var id: Long = 0L,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Record(
        override var id: Long = 0L,
        override val name: String,
        override val tags: Tags,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent
}