@file:OptIn(ExperimentalTime::class)

package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.platformPrintlnError
import io.github.smyrgeorge.log4k.impl.extensions.toJsonElement
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.time.ExperimentalTime

class SimpleJsonConsoleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.printJson()

    companion object {
        fun LoggingEvent.printJson() {
            val message = formatJson()
            if (level == Level.ERROR) platformPrintlnError(message)
            else println(message)
        }

        private fun LoggingEvent.formatJson(): String {
            val map = buildMap<String, Any?> {
                if (id > 0) put("id", id)
                put("level", level.name)
                put("span_id", span?.context?.spanId)
                put("trace_id", span?.context?.traceId)
                put("timestamp", timestamp)
                put("logger", logger)
                put("message", message.format(arguments))
                put("thread", thread)
                put("throwable", throwable?.stackTraceToString())
            }
            return map.toJsonElement().toString()
        }
    }
}