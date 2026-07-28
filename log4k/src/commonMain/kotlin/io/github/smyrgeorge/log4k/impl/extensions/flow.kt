package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Groups the flow into lists, emitting a batch as soon as it reaches [size] items
 * or [timeout] has elapsed since the first item of the batch arrived — whichever
 * comes first. Empty batches are never emitted: the timer is armed only while at
 * least one item is buffered. Any buffered remainder is flushed when the upstream
 * completes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.chunked(size: Int, timeout: Duration): Flow<List<T>> {
    require(size > 0) { "Size must be greater than 0." }
    require(timeout.isPositive()) { "Timeout must be greater than 0." }

    return channelFlow {
        val upstream: ReceiveChannel<T> = produce(capacity = size) { collect { send(it) } }
        // Ticks carry the batch generation they were armed for, so a tick that lost the
        // race against a size-based flush is recognised as stale and ignored.
        val ticks = Channel<Long>(Channel.UNLIMITED)
        val buffer = ArrayList<T>(size)
        var generation = 0L
        var timer: Job? = null

        suspend fun flush() {
            timer?.cancel()
            timer = null
            generation++
            if (buffer.isNotEmpty()) {
                send(buffer.toList())
                buffer.clear()
            }
        }

        whileSelect {
            // Checked before the upstream so an expired window flushes even under constant load.
            ticks.onReceive { generationOfTick ->
                if (generationOfTick == generation) flush()
                true
            }
            upstream.onReceiveCatching { result ->
                result.getOrNull()?.let { item ->
                    buffer.add(item)
                    if (buffer.size >= size) flush()
                    else if (timer == null) {
                        val armedFor = generation
                        timer = launch { delay(timeout); ticks.trySend(armedFor) }
                    }
                    true
                } ?: run {
                    flush()
                    result.exceptionOrNull()?.let { throw it }
                    false
                }
            }
        }
    }
}

internal inline fun <T> Flow<T>.preventFloodingWithBurst(
    requestsPerSecond: Int,
    burstDurationMillis: Int,
    burstResetPeriodMillis: Int,
    crossinline onDropMessages: (dropped: Int, totalDropped: Long) -> Unit =
        { dropped, totalDropped -> println("Dropped $dropped events due to flooding (total: $totalDropped).") }
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