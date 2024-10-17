package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.toName

class ConsoleAppender : Appender {
    override val name: String = this::class.toName()
    override fun append(event: LoggingEvent) {
        print(event.format())
        event.throwable?.printStackTrace()
    }

    private fun LoggingEvent.format(): String {
        var formatted = PATTERN
        formatted = formatted.replace("%idx", id.toString())
        formatted = formatted.replace("%d{HH:mm:ss.SSS}", timestamp.toString())
        formatted = formatted.replace("%-5level", level.name.padEnd(5))
        formatted = formatted.replace("%logger{36}", logger.take(36))
        formatted = formatted.replace("%msg", message.format(arguments))
        formatted = formatted.replace("%n", "\n")
        return formatted
    }

    companion object {
        private const val PATTERN = "%idx %d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"
    }
}