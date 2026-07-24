package io.github.smyrgeorge.log4k.utils

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.coroutines.channels.Channel

/**
 * An [Appender] test double that captures every [LoggingEvent] delivered to it through the real
 * `RootLogger -> Channel -> appender` pipeline.
 *
 * Like [CapturingTracingAppender] and [CapturingMeteringAppender], this exercises the *integration*
 * path: an event reaches this appender only if [io.github.smyrgeorge.log4k.Logger.log] built it (the
 * level gate passed) and it was consumed off the logging queue — which happens on a background
 * dispatcher. Register one per test, drive the logger, then await the specific event(s) before
 * asserting on them.
 *
 * The awaits filter by predicate and drain non-matching events, so a stray log from another test that
 * happens to be consumed inside this test's window cannot make an assertion pass or fail by accident.
 */
class CapturingLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()

    // UNLIMITED so `append` (called from the RootLogger consumer coroutine) never suspends or blocks;
    // the channel also provides the happens-before that publishes each event to the awaiting test.
    private val delivered = Channel<LoggingEvent>(Channel.UNLIMITED)

    override suspend fun append(event: LoggingEvent) {
        delivered.send(event)
    }

    /** Suspends until an event matching [predicate] is appended, draining any that do not match. */
    suspend fun awaitEvent(predicate: (LoggingEvent) -> Boolean = { true }): LoggingEvent {
        while (true) {
            val event = delivered.receive()
            if (predicate(event)) return event
        }
    }

    /** Suspends until [count] events matching [predicate] are appended and returns them in order. */
    suspend fun awaitEvents(count: Int, predicate: (LoggingEvent) -> Boolean = { true }): List<LoggingEvent> =
        buildList {
            while (size < count) {
                val event = delivered.receive()
                if (predicate(event)) add(event)
            }
        }
}
