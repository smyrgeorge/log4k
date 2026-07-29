package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.Tags
import kotlin.time.Instant

/**
 * Represents an event for logging purposes, capturing essential details about a specific logging occurrence.
 *
 * @property id A unique identifier for the logging event.
 * @property level The severity level of the event. It indicates the importance of the log entry.
 * @property span An optional tracing span associated with the log entry, which can be used for distributed tracing.
 * @property tags Structured key-value dimensions attached to the logging event.
 * @property timestamp The time at which the logging event was created.
 * @property logger The name of the logger that captured the event. Typically, this corresponds to the class or component name.
 * @property message The main logging message or content associated with the event.
 * @property arguments An array of arguments that are used to parameterize the log message.
 * @property thread The name of the thread in which the logging event was generated.
 * @property throwable An optional throwable associated with the logging event, capturing any exceptions or errors.
 * @property callSite The source location of the log call, injected at compile time by the
 *   `log4k-compiler-plugin` (see [SourceLocation]); `null` when the plugin is not applied.
 */
class LoggingEvent(
    val id: Long,
    val level: Level,
    val span: TracingEvent.Span?,
    val tags: Tags,
    val timestamp: Instant,
    val logger: String,
    val message: String,
    val arguments: Array<out Any?>,
    val thread: String,
    val throwable: Throwable?,
    val callSite: SourceLocation? = null,
) {
    /**
     * Mutable receiver of the builder-style logging DSL — [Logger.at] and its level-named extension
     * shorthands (`atTrace`, `atDebug`, `atInfo`, `atWarn`, `atError` in the
     * `io.github.smyrgeorge.log4k.impl.extensions` package). A fresh instance backs every
     * invocation, and each property maps onto one parameter of [Logger.log]:
     *
     * @property message The log message to be recorded ([Logger.log]'s `message`).
     * @property cause An optional throwable associated with the event ([Logger.log]'s `throwable`).
     * @property tags Structured key-value dimensions attached to the event ([Logger.log]'s `tags`).
     * @property span An optional span that can be used for tracing the context ([Logger.log]'s `span`).
     * @property arguments Additional arguments parameterizing the message ([Logger.log]'s `arguments`).
     */
    class Builder {
        var message: String = ""
        var cause: Throwable? = null
        var tags: Tags = emptyMap()
        var span: TracingEvent.Span? = null
        var arguments: Array<out Any?> = emptyArray()
    }
}
