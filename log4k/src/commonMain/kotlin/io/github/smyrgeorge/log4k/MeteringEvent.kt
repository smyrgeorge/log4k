package io.github.smyrgeorge.log4k

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

sealed interface MeteringEvent {
    var id: Long
    val name: String
    val attributes: Map<String, Any?>?
    val timestamp: Instant

    fun key(): Int = "$name.${attributes.hashCode()}".hashCode()

    data class CreateCountingInstrument(
        override var id: Long = 0L,
        override val name: String,
        override val attributes: Map<String, Any>?,
        override val timestamp: Instant = Clock.System.now(),
        val kind: Meter.Instrument.Kind,
        val initial: Number,
        val unit: String?,
        val description: String?,
    ) : MeteringEvent

    data class Add(
        override var id: Long = 0L,
        override val name: String,
        override val attributes: Map<String, Any>?,
        override val timestamp: Instant = Clock.System.now(),
        val value: Number,
    ) : MeteringEvent
}