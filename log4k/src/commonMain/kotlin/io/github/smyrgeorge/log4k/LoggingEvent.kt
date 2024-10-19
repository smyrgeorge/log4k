package io.github.smyrgeorge.log4k

import kotlinx.datetime.Instant

interface LoggingEvent {
    var id: Long
    val level: Level
    val timestamp: Instant
    val logger: String
    val message: String
    val arguments: Array<out Any?>
    val thread: String?
    val throwable: Throwable?
}