package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.datetime.Clock

fun <T> Flow<T>.preventFloodingWithBurst(
    requestsPerSecond: Int,
    burstDurationMillis: Int,
    onDropMessages: (dropped: Int, totalDropped: Long) -> Unit =
        { dropped, totalDropped -> println("Dropped $dropped log messages due to flooding (total: $totalDropped).") }
): Flow<T> {
    require(requestsPerSecond > 0) { "Requests per second must be greater than 0." }
    require(burstDurationMillis > 0) { "Burst duration must be greater than 0." }

    val windowMillis = 1000L / requestsPerSecond
    var lastEmissionTime = 0L
    var dropCounter = 0
    var totalDropped = 0L
    val startTime = Clock.System.now().toEpochMilliseconds()

    return this.transform { value ->
        val currentTime = Clock.System.now().toEpochMilliseconds()

        // Allow all messages during the burst period.
        if (currentTime - startTime < burstDurationMillis) {
            emit(value)
            lastEmissionTime = currentTime
        } else {
            // After burst period, start limiting.
            if (currentTime - lastEmissionTime >= windowMillis) {
                emit(value)
                lastEmissionTime = currentTime
                if (dropCounter > 0) {
                    totalDropped += dropCounter
                    onDropMessages(dropCounter, totalDropped)
                    dropCounter = 0
                }
            } else {
                dropCounter++
            }
        }
    }
}