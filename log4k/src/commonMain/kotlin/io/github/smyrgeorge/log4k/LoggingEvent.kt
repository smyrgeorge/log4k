package io.github.smyrgeorge.log4k

import kotlinx.datetime.Instant

/**
 * Represents a logging event that captures the details of a logging instance.
 *
 * This interface is designed to encapsulate all relevant information that pertains to a specific logging event.
 * It includes information such as the level of the log, the timestamp when the event occurred, the logger name,
 * the message, any associated arguments, the thread that triggered the log, and any exception that might have been thrown.
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