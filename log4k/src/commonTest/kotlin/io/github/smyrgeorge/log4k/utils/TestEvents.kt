package io.github.smyrgeorge.log4k.utils

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.TracingEvent
import kotlin.time.Instant

/**
 * Builds a [LoggingEvent] with deterministic defaults — a fixed epoch [timestamp] and a fixed
 * [thread] name — so appender-format tests can assert exact output strings on every platform.
 */
fun loggingEvent(
    id: Long = 1,
    level: Level = Level.INFO,
    span: TracingEvent.Span? = null,
    timestamp: Instant = Instant.fromEpochSeconds(0),
    logger: String = "test.logger",
    message: String = "hello {}",
    arguments: Array<out Any?> = arrayOf("world"),
    thread: String = "main",
    throwable: Throwable? = null,
): LoggingEvent = LoggingEvent(
    id = id,
    level = level,
    span = span,
    timestamp = timestamp,
    logger = logger,
    message = message,
    arguments = arguments,
    thread = thread,
    throwable = throwable,
)
