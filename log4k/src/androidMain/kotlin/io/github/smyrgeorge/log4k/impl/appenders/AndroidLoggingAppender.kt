package io.github.smyrgeorge.log4k.impl.appenders

import android.util.Log
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.toName

class AndroidLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.print()

    companion object {
        fun LoggingEvent.print() {
            val tag = logger.tag()

            // Check if logging is enabled for this level before formatting
            val priority = level.toPriority() ?: return
            if (!Log.isLoggable(tag, priority)) return

            val message = message.format(arguments)

            when (level) {
                Level.TRACE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
                Level.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
                Level.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
                Level.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                Level.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
                Level.OFF -> Unit
            }
        }

        private fun Level.toPriority(): Int? = when (this) {
            Level.TRACE -> Log.VERBOSE
            Level.DEBUG -> Log.DEBUG
            Level.INFO -> Log.INFO
            Level.WARN -> Log.WARN
            Level.ERROR -> Log.ERROR
            Level.OFF -> null
        }

        // Android log tags are limited to 23 characters.
        private fun String.tag(): String {
            if (length <= 23) return this

            // Try to use simple class name (last segment after '.')
            val lastDotIndex = lastIndexOf('.')
            if (lastDotIndex >= 0 && lastDotIndex < length - 1) {
                val simpleName = substring(lastDotIndex + 1)
                return if (simpleName.length <= 23) simpleName else simpleName.take(23)
            }

            // Fallback: take last 23 characters
            return takeLast(23)
        }
    }
}
