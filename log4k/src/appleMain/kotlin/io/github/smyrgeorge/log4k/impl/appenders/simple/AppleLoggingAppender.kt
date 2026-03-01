package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.toName
import platform.Foundation.NSLog

class AppleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.print()

    companion object {
        fun LoggingEvent.print() {
            if (level == Level.OFF) return
            val formatted = buildString {
                append("[${level.name}] $logger - ")
                append(message.format(arguments))
            }
            // Escape '%' only if present to prevent NSLog from treating the message as a format string.
            val escaped = if ('%' in formatted) formatted.replace("%", "%%") else formatted
            NSLog(escaped)

            // Log throwable details through NSLog for proper Apple logging integration
            throwable?.let { error ->
                NSLog("Exception: ${error.message ?: error::class.simpleName}")
                error.stackTraceToString().lines().forEach { line ->
                    NSLog("  $line")
                }
            }
        }
    }
}
