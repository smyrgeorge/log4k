package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.Meter.Instrument.Kind
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
        val labels: Map<String, Any>
        val value: Number
        override fun key(): Int = "$name.${labels.hashCode()}".hashCode()
    }

    data class Increment(
        override var id: Long = 0L,
        override val name: String,
        override val labels: Map<String, Any>,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Decrement(
        override var id: Long = 0L,
        override val name: String,
        override val labels: Map<String, Any>,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent

    data class Record(
        override var id: Long = 0L,
        override val name: String,
        override val labels: Map<String, Any>,
        override val timestamp: Instant = Clock.System.now(),
        override val value: Number,
    ) : ValueEvent
}