package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.impl.SimpleLoggingEvent
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender.Companion.print
import io.github.smyrgeorge.log4k.impl.extensions.preventFloodingWithBurst
import io.github.smyrgeorge.log4k.impl.extensions.thread
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

/**
 * A specialized implementation of `FlowAppender` designed for protective flooding control.
 * This abstract class manages events with a mechanism to prevent flooding and allow burst handling.
 *
 * @param requestPerSecond The number of requests per second to allow before throttling occurs.
 * @param burstDurationMillis The duration in milliseconds for which bursts are allowed.
 * @param burstResetPeriodMillis The period in milliseconds after which the burst state resets. Default value is 5000 milliseconds.
 *
 * This class ensures that the flow of events does not exceed the specified rate and implements a burst logic to temporarily allow higher rates.
 * If the rate exceeds the allowed limits, excess events are dropped, and a logging event is generated to warn about the dropped events.
 */
abstract class FlowFloodProtectedAppender<T>(
    private val requestPerSecond: Int,
    private val burstDurationMillis: Int,
    private val burstResetPeriodMillis: Int = 5000,
) : FlowAppender<T, T>() {
    override fun setup(flow: Flow<T>): Flow<T> =
        flow.preventFloodingWithBurst(requestPerSecond, burstDurationMillis, burstResetPeriodMillis) { d, t ->
            SimpleLoggingEvent(
                id = 0,
                level = Level.WARN,
                span = null,
                timestamp = Clock.System.now(),
                logger = "internal.FlowFloodProtectedAppender",
                message = "Dropped $d log messages due to flooding (total dropped: $t).",
                arguments = emptyArray(),
                thread = thread(),
                throwable = null,
            ).print()
        }
}