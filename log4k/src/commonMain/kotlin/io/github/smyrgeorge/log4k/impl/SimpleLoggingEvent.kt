package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.TracingEvent
import kotlinx.datetime.Instant

class SimpleLoggingEvent(
    override var id: Long = 0,
    override val level: Level,
    override val span: TracingEvent.Span?,
    override val timestamp: Instant,
    override val logger: String,
    override val message: String,
    override val arguments: Array<out Any?>,
    override val thread: String,
    override val throwable: Throwable?,
) : LoggingEvent