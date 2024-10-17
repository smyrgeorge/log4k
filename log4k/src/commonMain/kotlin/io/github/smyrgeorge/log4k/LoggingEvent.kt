package io.github.smyrgeorge.log4k

import kotlinx.datetime.Instant

class LoggingEvent(
    var id: Long = 0,
    val level: Level,
    val logger: String,
    val message: String,
    val arguments: Array<out Any?>,
    val timestamp: Instant,
    val thread: String? = null,
    val throwable: Throwable? = null,
)