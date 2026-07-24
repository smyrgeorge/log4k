package io.github.smyrgeorge.log4k.utils

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.TracingEvent.Span
import kotlin.time.Clock

/**
 * A [Logger] test double that synchronously captures every [LoggingEvent] it builds.
 *
 * [Logger.log] invokes [toLoggingEvent] only *after* the level gate (`level.enabled()`) passes, so
 * [events] contains exactly what this logger decided to emit — no more, no less. That makes it the
 * right tool for unit-testing [Logger] itself: level filtering, message/argument/span/throwable
 * propagation and the `logged(...)` entry/exit lines can all be asserted deterministically, without
 * touching the asynchronous `RootLogger -> Channel -> appender` pipeline.
 *
 * NOTE: [Logger.log] is not `open` and always forwards the built event to the global [RootLogger].
 * Capturing does not depend on that path, but that forward would otherwise print through the default
 * console appender — [LoggerTest] detaches the logging appenders for the duration of each test to keep
 * output clean.
 *
 * @param name the logger name reported on every captured event.
 * @param level the threshold; defaults to [Level.TRACE] so nothing is filtered unless a test opts in.
 */
class CapturingLogger(
    name: String = "test.capturing",
    level: Level = Level.TRACE,
) : Logger(name, level) {

    /** Every event built by this logger, in emission order. */
    val events: MutableList<LoggingEvent> = mutableListOf()

    /** The most recently captured event. Throws if nothing has been captured yet. */
    val last: LoggingEvent get() = events.last()

    /** Number of captured events. */
    val size: Int get() = events.size

    /** Drops all captured events, e.g. between logical sections of a test. */
    fun clear(): Unit = events.clear()

    private var seq: Long = 0

    override fun toLoggingEvent(
        level: Level,
        span: Span?,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?
    ): LoggingEvent = LoggingEvent(
        id = seq++,
        level = level,
        span = span,
        timestamp = Clock.System.now(),
        logger = name,
        message = message,
        arguments = arguments,
        thread = "test",
        throwable = throwable,
    ).also { events += it }
}
