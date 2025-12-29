package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleTracingAppender : Appender<TracingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: TracingEvent) {
        println(event.toString())
    }
}
