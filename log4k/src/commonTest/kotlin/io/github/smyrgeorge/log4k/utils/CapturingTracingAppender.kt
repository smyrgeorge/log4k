package io.github.smyrgeorge.log4k.utils

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.coroutines.channels.Channel

/**
 * An [Appender] test double that captures every [TracingEvent] delivered to it through the real
 * `RootLogger -> Channel -> appender` pipeline.
 *
 * Unlike a plain unit-test double, this exercises the *integration* path: a span reaches this appender
 * only if it was emitted (`Local.end()` -> `RootLogger.trace(...)`) and consumed off the tracing queue.
 * That consumption happens on a background dispatcher, so an event is **not** guaranteed to be present
 * the instant it is emitted. Register one per test, trigger the tracing, then [awaitSpan]/[awaitEvent]
 * to suspend until the expected span has actually been appended before asserting on it.
 *
 * The awaits filter by predicate and drain non-matching events, so a stray span from another test that
 * happens to be consumed inside this test's window cannot make an assertion pass or fail by accident.
 */
class CapturingTracingAppender : Appender<TracingEvent> {
    override val name: String = this::class.toName()

    // UNLIMITED so `append` (called from the RootLogger consumer coroutine) never suspends or blocks;
    // the channel also provides the happens-before that publishes each event to the awaiting test.
    private val delivered = Channel<TracingEvent>(Channel.UNLIMITED)

    override suspend fun append(event: TracingEvent) {
        delivered.send(event)
    }

    /** Suspends until an event matching [predicate] is appended, draining any that do not match. */
    suspend fun awaitEvent(predicate: (TracingEvent) -> Boolean = { true }): TracingEvent {
        while (true) {
            val event = delivered.receive()
            if (predicate(event)) return event
        }
    }

    /** Suspends until a [TracingEvent.Span] named [name] is appended and returns it. */
    suspend fun awaitSpan(name: String): TracingEvent.Span =
        awaitEvent { it is TracingEvent.Span && it.name == name } as TracingEvent.Span
}
