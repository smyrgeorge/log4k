package io.github.smyrgeorge.log4k

import kotlinx.datetime.Instant

interface MeteringEvent {
    class Measurement(
        val timestamp: Instant,
        val instrument: Meter.Instrument
    ) : MeteringEvent
}