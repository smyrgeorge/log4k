package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.print()

    companion object {
        fun LoggingEvent.print() {
            print(format())
            throwable?.printStackTrace()
        }

        private fun LoggingEvent.format(): String = buildString {
            append(id)
            append(' ')
            append(span?.context?.spanId?.let { "[$it] " } ?: " ")
            append(timestamp)
            append(" [")
            append(thread)
            append("] - ")
            append(level.name.padEnd(5))
            append(' ')
            append(logger)
            append(" - ")
            append(message.format(arguments))
            appendLine()
        }
    }
}