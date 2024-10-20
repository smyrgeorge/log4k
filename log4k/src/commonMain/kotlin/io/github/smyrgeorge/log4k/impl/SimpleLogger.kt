package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.TracingEvent.Span
import kotlinx.datetime.Clock

class SimpleLogger(name: String, level: Level) : Logger(name, level) {
    override fun toLoggingEvent(
        level: Level,
        span: Span?,
        msg: String,
        throwable: Throwable?,
        args: Array<out Any?>
    ): LoggingEvent {
        return SimpleLoggingEvent(
            level = level,
            span = span,
            timestamp = Clock.System.now(),
            logger = name,
            message = msg,
            arguments = args,
            thread = null,
            throwable = throwable
        )
    }
}