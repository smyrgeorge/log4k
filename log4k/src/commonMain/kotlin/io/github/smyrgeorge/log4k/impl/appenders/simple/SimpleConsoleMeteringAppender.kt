package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleMeteringAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: MeteringEvent) {
        println(event.toString())
    }
}