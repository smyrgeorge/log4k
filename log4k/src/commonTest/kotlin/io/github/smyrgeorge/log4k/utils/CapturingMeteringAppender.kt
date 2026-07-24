package io.github.smyrgeorge.log4k.utils

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.coroutines.channels.Channel

/**
 * An [Appender] test double that captures every [MeteringEvent] delivered to it through the real
 * `RootLogger -> Channel -> appender` pipeline.
 *
 * Like [CapturingTracingAppender], this exercises the *integration* path: an event reaches this
 * appender only if a [io.github.smyrgeorge.log4k.Meter] instrument emitted it (instrument creation,
 * `increment`/`set`/`decrement`/`record`) and it was consumed off the metering queue — which happens
 * on a background dispatcher. Register one per test, drive the meter, then await the specific event
 * before asserting on it.
 *
 * The awaits filter by predicate and drain non-matching events, so a stray event from another test
 * that happens to be consumed inside this test's window cannot make an assertion pass or fail by
 * accident.
 */
class CapturingMeteringAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()

    // UNLIMITED so `append` (called from the RootLogger consumer coroutine) never suspends or blocks;
    // the channel also provides the happens-before that publishes each event to the awaiting test.
    private val delivered = Channel<MeteringEvent>(Channel.UNLIMITED)

    override suspend fun append(event: MeteringEvent) {
        delivered.send(event)
    }

    /** Suspends until an event matching [predicate] is appended, draining any that do not match. */
    suspend fun awaitEvent(predicate: (MeteringEvent) -> Boolean = { true }): MeteringEvent {
        while (true) {
            val event = delivered.receive()
            if (predicate(event)) return event
        }
    }

    /** Suspends until the [MeteringEvent.CreateInstrument] for the instrument [name] is appended. */
    suspend fun awaitCreate(name: String): MeteringEvent.CreateInstrument =
        awaitEvent { it is MeteringEvent.CreateInstrument && it.name == name } as MeteringEvent.CreateInstrument

    /** Suspends until a value event (`Increment`/`Set`/`Decrement`/`Record`) named [name] is appended. */
    suspend fun awaitValue(name: String): MeteringEvent.ValueEvent =
        awaitEvent { it is MeteringEvent.ValueEvent && it.name == name } as MeteringEvent.ValueEvent
}
