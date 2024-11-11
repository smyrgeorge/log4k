package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.CoLogger
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.thread
import kotlinx.datetime.Clock

class SimpleCoLogger(name: String, level: Level) : CoLogger(name, level) {
    override fun toLoggingEvent(
        level: Level,
        ctx: LoggingContext,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?
    ): LoggingEvent {
        return SimpleCoLoggingEvent(
            level = level,
            span = ctx.span,
            timestamp = Clock.System.now(),
            logger = name,
            message = message,
            arguments = arguments,
            thread = thread(),
            throwable = throwable,
            ctx = ctx,
        )
    }
}