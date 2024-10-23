package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.impl.SimpleLoggingEvent
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender.Companion.print
import io.github.smyrgeorge.log4k.impl.extensions.preventFloodingWithBurst
import io.github.smyrgeorge.log4k.impl.extensions.thread
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

@Suppress("unused")
abstract class FlowFloodProtectedAppender<T>(
    private val requestPerSecond: Int,
    private val burstDurationMillis: Int
) : FlowAppender<T, T>() {
    override fun setup(flow: Flow<T>): Flow<T> =
        flow.preventFloodingWithBurst(requestPerSecond, burstDurationMillis) { d, t ->
            SimpleLoggingEvent(
                id = 0,
                level = Level.WARN,
                span = null,
                timestamp = Clock.System.now(),
                logger = "FlowFloodProtectedAppender",
                message = "Dropped $d log messages due to flooding (total: $t).",
                arguments = emptyArray(),
                thread = thread(),
                throwable = null,
            ).print()
        }
}