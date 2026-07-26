package io.github.smyrgeorge.log4k.slf4j.utils

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import kotlinx.coroutines.channels.Channel

/**
 * An [Appender] test double that captures every [LoggingEvent] delivered to it through the real
 * `RootLogger -> Channel -> appender` pipeline.
 *
 * Mirrors the capturing appender used by the core module's tests, and for the same reason: an event
 * reaches this appender only if it travelled the whole way — SLF4J call site, `Log4kLogger`, SLF4J's
 * argument normalization, [io.github.smyrgeorge.log4k.Logger.log] (level gate included), and the
 * logging queue, which is consumed on a background dispatcher. Register one per test, drive the
 * SLF4J logger, then await the specific event(s) before asserting on them.
 *
 * The awaits filter by predicate and drain non-matching events, so a stray log from elsewhere that
 * happens to be consumed inside this test's window cannot make an assertion pass or fail by accident.
 */
class CapturingLoggingAppender : Appender<LoggingEvent> {
    override val name: String = "CapturingLoggingAppender"

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
}
