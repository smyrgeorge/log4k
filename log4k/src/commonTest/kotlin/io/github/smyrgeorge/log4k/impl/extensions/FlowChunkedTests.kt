package io.github.smyrgeorge.log4k.impl.extensions

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the size-or-timeout [chunked] operator used by `BatchAppender`. All timing is
 * virtual (via `runTest`), so the expected batch boundaries are fully deterministic.
 */
class FlowChunkedTests {

    @Test
    fun sizeReached_emitsFullBatchesWithoutWaitingForTimeout() = runTest {
        val results = flow {
            repeat(10) { emit(it) }
        }.chunked(size = 5, timeout = 1.minutes).toList()

        assertThat(results).isEqualTo(listOf(listOf(0, 1, 2, 3, 4), listOf(5, 6, 7, 8, 9)))
    }

    @Test
    fun timeoutElapsed_emitsPartialBatch() = runTest {
        val results = flow {
            emit(1)
            emit(2)
            delay(2.seconds) // Exceeds the timeout, so [1, 2] must flush at the 1-second mark.
            emit(3)
        }.chunked(size = 5, timeout = 1.seconds).toList()

        assertThat(results).isEqualTo(listOf(listOf(1, 2), listOf(3)))
    }

    @Test
    fun timeout_isMeasuredFromFirstItemOfBatch_notFromLastItem() = runTest {
        val results = flow {
            emit(1) // t=0.0s: batch starts, deadline at t=1.0s.
            delay(600.milliseconds)
            emit(2) // t=0.6s: must NOT extend the deadline.
            delay(600.milliseconds)
            emit(3) // t=1.2s: [1, 2] already flushed at t=1.0s, so 3 starts a new batch.
        }.chunked(size = 10, timeout = 1.seconds).toList()

        assertThat(results).isEqualTo(listOf(listOf(1, 2), listOf(3)))
    }

    @Test
    fun upstreamCompletes_flushesRemainderImmediately() = runTest {
        val results = flow {
            emit(1)
            emit(2)
            emit(3)
        }.chunked(size = 5, timeout = 1.minutes).toList()

        assertThat(results).isEqualTo(listOf(listOf(1, 2, 3)))
    }

    @Test
    fun idleUpstream_neverEmitsEmptyBatches() = runTest {
        val results = flow {
            delay(5.seconds) // Several timeout windows pass with an empty buffer.
            emit(1)
        }.chunked(size = 5, timeout = 1.seconds).toList()

        assertThat(results).isEqualTo(listOf(listOf(1)))
    }

    @Test
    fun emptyUpstream_completesWithoutEmitting() = runTest {
        val results = flow<Int> {}.chunked(size = 5, timeout = 1.seconds).toList()
        assertThat(results).isEqualTo(emptyList())
    }

    @Test
    fun consecutiveTimeoutWindows_eachFlushTheirOwnBatch() = runTest {
        val results = flow {
            emit(1)
            delay(1500.milliseconds) // [1] flushes at t=1.0s.
            emit(2)
            delay(1500.milliseconds) // [2] flushes at t=2.5s.
            emit(3)
        }.chunked(size = 5, timeout = 1.seconds).toList()

        assertThat(results).isEqualTo(listOf(listOf(1), listOf(2), listOf(3)))
    }

    @Test
    fun invalidArguments_throw() {
        val flow = flow<Int> {}
        assertFailsWith<IllegalArgumentException> { flow.chunked(size = 0, timeout = 1.seconds) }
        assertFailsWith<IllegalArgumentException> { flow.chunked(size = 5, timeout = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { flow.chunked(size = 5, timeout = (-1).seconds) }
    }

    @Test
    fun upstreamFailure_propagatesToCollector() = runTest {
        var failed = false
        try {
            flow {
                emit(1)
                error("boom")
            }.chunked(size = 5, timeout = 1.minutes).collect {}
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertThat(failed).isTrue()
    }
}
