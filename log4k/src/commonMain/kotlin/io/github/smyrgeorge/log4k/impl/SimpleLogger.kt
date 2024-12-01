package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.extensions.thread
import kotlinx.datetime.Clock

class SimpleLogger(name: String, level: Level) : Logger(name, level) {
    override fun toLoggingEvent(
        level: Level,
        span: Span?,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?
    ): LoggingEvent {
        return LoggingEvent(
            level = level,
            span = span,
            timestamp = Clock.System.now(),
            logger = name,
            message = message,
            arguments = arguments,
            thread = thread(),
            throwable = throwable
        )
    }
}