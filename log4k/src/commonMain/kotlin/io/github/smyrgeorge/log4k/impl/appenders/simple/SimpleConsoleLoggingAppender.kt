package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.platformPrintlnError
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.print()

    companion object {
        fun LoggingEvent.print() {
            val message = format()
            if (level == Level.ERROR) platformPrintlnError(message)
            else println(message)
            throwable?.printStackTrace()
        }

        private fun LoggingEvent.format(): String = buildString {
            if (id > 0) {
                append(id)
                append(' ')
            }
            append(span?.context?.spanId?.let { "[$it] " } ?: "")
            append(timestamp)
            append(" [")
            append(thread)
            append("] - ")
            append(level.name.padEnd(5))
            append(' ')
            append(logger)
            append(" - ")
            append(message.format(arguments))
        }
    }
}