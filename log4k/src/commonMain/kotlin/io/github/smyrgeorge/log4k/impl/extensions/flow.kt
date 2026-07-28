package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect
import kotlin.time.Duration
import kotlin.time.TimeSource

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

/** Fixed origin for the limiter's monotonic timeline; the absolute values are meaningless, only deltas matter. */
internal val rateLimiterTimeOrigin: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

/**
 * Rate-limits the flow to roughly [requestsPerSecond] by enforcing a minimum gap between
 * emissions, while tolerating short spikes: the first too-fast event opens a *burst window* of
 * [burstDurationMillis] during which everything is emitted. After the window closes, excess
 * events are dropped (and counted) until the rate falls back under the limit. A new burst is
 * allowed only once [burstResetPeriodMillis] has passed since the previous burst started —
 * it must be at least [burstDurationMillis], otherwise a burst could be reset while still
 * active, effectively disabling the protection.
 *
 * [onDropMessages] is invoked with the number of events dropped since the previous report and
 * the cumulative total — on the next successful emission, and once more when the upstream
 * completes with drops still pending.
 *
 * Timing uses a monotonic clock with millisecond granularity, so rates above 1000/s cannot be
 * distinguished and are limited to one event per millisecond. [nowMillis] exists for tests.
 */
internal fun <T> Flow<T>.preventFloodingWithBurst(
    requestsPerSecond: Int,
    burstDurationMillis: Int,
    burstResetPeriodMillis: Int,
    nowMillis: () -> Long = { rateLimiterTimeOrigin.elapsedNow().inWholeMilliseconds },
    onDropMessages: (dropped: Int, totalDropped: Long) -> Unit =
        { dropped, totalDropped -> println("Dropped $dropped events due to flooding (total: $totalDropped).") }
): Flow<T> {
    require(requestsPerSecond > 0) { "Requests per second must be greater than 0." }
    require(burstDurationMillis > 0) { "Burst duration must be greater than 0." }
    require(burstResetPeriodMillis >= burstDurationMillis) {
        "Burst reset period must be greater than or equal to the burst duration."
    }

    // A millisecond clock cannot space events closer than 1ms apart, so rates above 1000/s
    // are capped there instead of degenerating to a zero-width window that admits everything.
    val windowMillis = (1000L / requestsPerSecond).coerceAtLeast(1L)

    return flow {
        // State lives inside the builder so every collection of the returned flow starts fresh.
        var lastEmissionTime = -windowMillis // Guarantees the very first event passes.
        var burstStartTime = -1L // -1 marks "no burst in progress".
        var dropCounter = 0
        var totalDropped = 0L

        fun reportDrops() {
            if (dropCounter > 0) {
                totalDropped += dropCounter
                onDropMessages(dropCounter, totalDropped)
                dropCounter = 0
            }
        }

        collect { value ->
            val currentTime = nowMillis()
            if (currentTime - lastEmissionTime >= windowMillis) {
                // Under the allowed rate. Also expire a past burst once its reset period elapses.
                if (burstStartTime != -1L && currentTime - burstStartTime > burstResetPeriodMillis) burstStartTime = -1L
                emit(value)
                lastEmissionTime = currentTime
                reportDrops()
            } else {
                if (burstStartTime == -1L) burstStartTime = currentTime
                if (currentTime - burstStartTime <= burstDurationMillis) {
                    // Allow all messages during the burst period.
                    emit(value)
                    lastEmissionTime = currentTime
                    reportDrops()
                } else {
                    // After the burst period, start limiting.
                    dropCounter++
                }
            }
        }
        // Do not lose a pending drop count when the upstream completes.
        reportDrops()
    }
}