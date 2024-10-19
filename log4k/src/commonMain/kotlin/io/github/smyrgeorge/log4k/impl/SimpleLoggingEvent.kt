package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import kotlinx.datetime.Instant

class SimpleLoggingEvent(
    override var id: Long = 0,
    override val level: Level,
    override val timestamp: Instant,
    override val logger: String,
    override val message: String,
    override val arguments: Array<out Any?>,
    override val thread: String? = null,
    override val throwable: Throwable? = null,
) : LoggingEvent