package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlin.time.Clock

inline fun <T> Flow<T>.preventFloodingWithBurst(
    requestsPerSecond: Int,
    burstDurationMillis: Int,
    burstResetPeriodMillis: Int,
    crossinline onDropMessages: (dropped: Int, totalDropped: Long) -> Unit =
        { dropped, totalDropped -> println("Dropped $dropped log messages due to flooding (total: $totalDropped).") }
): Flow<T> {
    require(requestsPerSecond > 0) { "Requests per second must be greater than 0." }
    require(burstDurationMillis > 0) { "Burst duration must be greater than 0." }

    val windowMillis = 1000L / requestsPerSecond
    var lastEmissionTime = 0L
    var dropCounter = 0
    var totalDropped = 0L
    var startBurstTime: Long = 0

    return transform { value ->
        val currentTime = Clock.System.now().toEpochMilliseconds()
        if (currentTime - lastEmissionTime >= windowMillis) {
            if (currentTime - startBurstTime > burstResetPeriodMillis) startBurstTime = 0
            emit(value)
            lastEmissionTime = currentTime
            if (dropCounter > 0) {
                totalDropped += dropCounter
                onDropMessages(dropCounter, totalDropped)
                dropCounter = 0
            }
        } else {
            if (startBurstTime == 0L) startBurstTime = currentTime
            if (currentTime - startBurstTime <= burstDurationMillis) {
                // Allow all messages during the burst period.
                emit(value)
                lastEmissionTime = currentTime
                if (dropCounter > 0) {
                    totalDropped += dropCounter
                    onDropMessages(dropCounter, totalDropped)
                    dropCounter = 0
                }
            } else {
                // After burst period, start limiting.
                dropCounter++
            }
        }
    }
}