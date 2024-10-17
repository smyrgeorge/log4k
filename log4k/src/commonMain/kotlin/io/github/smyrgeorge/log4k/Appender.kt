package io.github.smyrgeorge.log4k

interface Appender {
    val name: String
    fun append(event: LoggingEvent)
}