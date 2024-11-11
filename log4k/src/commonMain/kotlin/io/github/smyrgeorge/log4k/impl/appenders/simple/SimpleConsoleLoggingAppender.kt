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
            if (id > 0) append(id).append(' ')
            append(span?.context?.spanId?.let { "[$it] " } ?: "")
            append(timestamp)
            append(" [")
            append(thread)
            append("] - ")
            append(level.name.padEnd(5))
            append(' ')
            if (logger.length > 36) append(logger.compact()) else append(logger)
            append(" - ")
            append(message.format(arguments))
        }

        private fun String.compact(): String = buildString {
            if (this@compact.isEmpty()) return@buildString
            if (!this@compact.contains('.')) {
                append(this@compact)
                return@buildString
            }
            val parts = this@compact.split('.')
            if (parts.size < 2) {
                append(this@compact)
                return@buildString
            }
            val res = parts.take(parts.size - 2).joinToString(".") { it.first().toString() }
            append(res).append('.').append(parts.takeLast(2).joinToString("."))
        }
    }
}