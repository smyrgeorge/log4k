package io.github.smyrgeorge.log4k

import kotlinx.datetime.Instant

/**
 * Represents an event for logging purposes.
 *
 * This interface defines the structure for a logging event
 * which includes various details about the event such as
 * the logging level, timestamp, logger name, message,
 * arguments, thread, and any associated throwable.
 */
interface LoggingEvent {
    var id: Long
    val level: Level
    val span: TracingEvent.Span?
    val timestamp: Instant
    val logger: String
    val message: String
    val arguments: Array<out Any?>
    val thread: String
    val throwable: Throwable?
}