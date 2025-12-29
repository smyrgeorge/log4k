package io.github.smyrgeorge.log4k

import kotlin.time.Instant

/**
 * Represents an event for logging purposes, capturing essential details about a specific logging occurrence.
 *
 * @property id A unique identifier for the logging event.
 * @property level The severity level of the event. It indicates the importance of the log entry.
 * @property span An optional tracing span associated with the log entry, which can be used for distributed tracing.
 * @property timestamp The time at which the logging event was created.
 * @property logger The name of the logger that captured the event. Typically, this corresponds to the class or component name.
 * @property message The main logging message or content associated with the event.
 * @property arguments An array of arguments that are used to parameterize the log message.
 * @property thread The name of the thread in which the logging event was generated.
 * @property throwable An optional throwable associated with the logging event, capturing any exceptions or errors.
 */
class LoggingEvent(
    var id: Long = 0,
    val level: Level,
    val span: TracingEvent.Span?,
    val timestamp: Instant,
    val logger: String,
    val message: String,
    val arguments: Array<out Any?>,
    val thread: String,
    val throwable: Throwable?,
)
