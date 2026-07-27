package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.print()

    companion object {
        fun LoggingEvent.print() {
            val message = format()
            println(message)
        }

        internal fun LoggingEvent.format(colors: Boolean = true): String = buildString {
            if (id > 0) append(id).append(' ')
            val span = span?.context?.spanId?.let { "[$it] " } ?: ""
            if (colors) append(span.purple()) else append(span)
            if (colors) append(timestamp.toString().green()) else append(timestamp)
            append(" [")
            append(thread)
            append("] - ")
            val level = if (colors) level.colour() else level.name
            append(level)
            append(' ')
            val logger = if (logger.length > 36) logger.compact() else logger
            if (colors) append(logger.cyan()) else append(logger)
            append(" - ")
            append(message.format(arguments))
            // Keep the stack trace in the same string as its log line: printing it separately
            // (e.g. via printStackTrace) goes to a different stream and gets detached/reordered.
            throwable?.let {
                appendLine()
                append(it.stackTraceToString().trimEnd())
            }
        }

        private const val ESC = "\u001B["
        private fun String.red(): String = "${ESC}31m$this${ESC}0m"
        private fun String.green(): String = "${ESC}32m$this${ESC}0m"
        private fun String.yellow(): String = "${ESC}33m$this${ESC}0m"
        private fun String.blue(): String = "${ESC}34m$this${ESC}0m"
        private fun String.purple(): String = "${ESC}35m$this${ESC}0m"
        private fun String.cyan(): String = "${ESC}36m$this${ESC}0m"
        private fun String.grey(): String = "${ESC}90m$this${ESC}0m"
        private fun Level.colour(): String = when (this) {
            Level.TRACE -> name.grey()
            Level.DEBUG -> name.grey()
            Level.INFO -> name.blue()
            Level.WARN -> name.yellow()
            Level.ERROR -> name.red()
            Level.OFF -> name
        }

        private fun String.compact(): String = buildString {
            if (this@compact.isEmpty()) return@buildString
            val parts = this@compact.split('.')
            if (parts.size < 3) {
                append(this@compact)
                return@buildString
            }
            val res = parts.take(parts.size - 2).joinToString(".") { it.firstOrNull()?.toString() ?: "" }
            append(res).append('.').append(parts.takeLast(2).joinToString("."))
        }
    }
}
