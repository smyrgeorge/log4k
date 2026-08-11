package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.toJsonElement
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleJsonConsoleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.printJson()

    companion object {
        /**
         * Registers a [SimpleJsonConsoleLoggingAppender] with the logging appender registry — by default
         * unregistering every other logging appender in the same atomic step (see
         * [io.github.smyrgeorge.log4k.impl.registry.AppenderRegistry.install]).
         *
         * @param unregisterOthers Whether to unregister every other logging appender (default `true`).
         * @return The registered appender, e.g. for later unregistration.
         */
        fun install(unregisterOthers: Boolean = true): SimpleJsonConsoleLoggingAppender =
            RootLogger.Logging.appenders.install(SimpleJsonConsoleLoggingAppender(), unregisterOthers)

        fun LoggingEvent.printJson() {
            val message = formatJson()
            println(message)
        }

        internal fun LoggingEvent.formatJson(): String {
            val map = buildMap {
                if (id > 0) put("id", id)
                put("level", level.name)
                put("span_id", span?.context?.spanId)
                put("trace_id", span?.context?.traceId)
                put("timestamp", timestamp)
                put("logger", logger)
                put("message", message.format(arguments))
                put("tags", tags)
                put("thread", thread)
                put("throwable", throwable?.stackTraceToString())
                callSite?.let {
                    put("caller_method_name", it.function.substringAfterLast('.').takeIf(String::isNotBlank))
                    put("caller_file_name", it.file)
                    put("caller_line_number", it.line)
                }
            }
            return map.toJsonElement().toString()
        }
    }
}