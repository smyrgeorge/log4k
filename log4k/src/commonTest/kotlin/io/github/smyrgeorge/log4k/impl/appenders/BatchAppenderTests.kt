package io.github.smyrgeorge.log4k.impl.appenders

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for [BatchAppender]. The appender runs on a real background dispatcher, so the
 * timeout-related tests use short *real* durations and simply suspend on the capture channel
 * until the batch arrives — no wall-clock assertions, only batch contents, to stay
 * deterministic on slow CI machines.
 */
class BatchAppenderTests {

    private class CapturingBatchAppender(size: Int, timeout: Duration? = null) :
        BatchAppender<Int>(size, timeout) {
        val batches = Channel<List<Int>>(Channel.UNLIMITED)
        override suspend fun handle(event: List<Int>) {
            batches.send(event)
        }
    }

    @Test
    fun append_groupsEventsIntoFixedSizeBatches() = runTest {
        val appender = CapturingBatchAppender(size = 3)
        repeat(6) { appender.append(it + 1) }
        assertThat(appender.batches.receive()).isEqualTo(listOf(1, 2, 3))
        assertThat(appender.batches.receive()).isEqualTo(listOf(4, 5, 6))
    }

    @Test
    fun append_withoutTimeout_neverFlushesPartialBatches() = runTest {
        val appender = CapturingBatchAppender(size = 3)
        repeat(4) { appender.append(it + 1) }
        assertThat(appender.batches.receive()).isEqualTo(listOf(1, 2, 3))
        // Give the pipeline real time to (incorrectly) flush the leftover event.
        withContext(Dispatchers.Default) { delay(150.milliseconds) }
        assertThat(appender.batches.tryReceive().getOrNull()).isNull()
    }

    @Test
    fun append_withTimeout_flushesPartialBatchWhenTimeExpires() = runTest {
        val appender = CapturingBatchAppender(size = 10, timeout = 100.milliseconds)
        appender.append(1)
        appender.append(2)
        appender.append(3)
        assertThat(appender.batches.receive()).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun append_withTimeout_sizeStillFlushesFirst() = runTest {
        // If the timeout wrongly gated size-based flushes, the receives below would hang
        // for 5 minutes and trip runTest's timeout.
        val appender = CapturingBatchAppender(size = 2, timeout = 5.minutes)
        repeat(4) { appender.append(it + 1) }
        assertThat(appender.batches.receive()).isEqualTo(listOf(1, 2))
        assertThat(appender.batches.receive()).isEqualTo(listOf(3, 4))
    }

    @Test
    fun append_withTimeout_timerRearmsForEachNewBatch() = runTest {
        val appender = CapturingBatchAppender(size = 10, timeout = 100.milliseconds)
        appender.append(1)
        assertThat(appender.batches.receive()).isEqualTo(listOf(1))
        appender.append(2)
        assertThat(appender.batches.receive()).isEqualTo(listOf(2))
    }
}
