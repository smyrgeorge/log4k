package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override fun append(event: LoggingEvent) {
        print(event.format())
        event.throwable?.printStackTrace()
    }

    private fun LoggingEvent.format(): String = buildString {
        append(id)
        append(' ')
        append(span?.id?.let { " [$it] " } ?: " ")
        append(timestamp)
        append(' ')
        append(level.name.padEnd(5))
        append(' ')
        append(logger.take(36))
        append(' ')
        append(message.format(arguments))
        append('\n')
    }
}