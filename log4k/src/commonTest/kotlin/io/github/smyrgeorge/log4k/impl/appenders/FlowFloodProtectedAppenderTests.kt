package io.github.smyrgeorge.log4k.impl.appenders

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for [FlowFloodProtectedAppender]. The rate-limiting logic itself is covered
 * deterministically (with a fake clock) in `FlowPreventFloodingTests`; these tests exercise the
 * appender end-to-end on the real pipeline, so the flooding test uses real delays and asserts
 * loose bounds instead of exact counts to stay stable on slow CI machines.
 */
class FlowFloodProtectedAppenderTests {

    private class CapturingFloodAppender(
        requestPerSecond: Int,
        burstDurationMillis: Int,
        burstResetPeriodMillis: Int = 5000,
    ) : FlowFloodProtectedAppender<Int>(requestPerSecond, burstDurationMillis, burstResetPeriodMillis) {
        val handled = Channel<Int>(Channel.UNLIMITED)
        override suspend fun handle(event: Int) {
            handled.send(event)
        }
    }

    @Test
    fun underTheRate_allEventsAreDelivered() = runTest {
        // A 5-second burst window comfortably covers the whole test, so nothing may be dropped.
        val appender = CapturingFloodAppender(requestPerSecond = 1000, burstDurationMillis = 5000)
        repeat(20) { appender.append(it) }
        val received = List(20) { appender.handled.receive() }
        assertThat(received).isEqualTo((0 until 20).toList())
    }

    @Test
    fun flooding_dropsExcessEvents_andRecoversAfterwards() = runTest {
        // 10/s -> 100ms window; the 1ms burst can only admit the event that opens it.
        val appender = CapturingFloodAppender(requestPerSecond = 10, burstDurationMillis = 1)
        appender.append(0)
        assertThat(appender.handled.receive()).isEqualTo(0) // The limiter is primed.

        // Flood: 30 events, each far inside the 100ms window relative to the last emission.
        repeat(30) {
            appender.append(it + 1)
            withContext(Dispatchers.Default) { delay(5.milliseconds) }
        }

        // Recover: after a full window of silence the sentinel must pass again.
        withContext(Dispatchers.Default) { delay(150.milliseconds) }
        appender.append(999)

        val received = buildList {
            while (true) {
                val event = appender.handled.receive()
                add(event)
                if (event == 999) break
            }
        }
        // The burst admits ~1 flood event and the 100ms window lets a couple more trickle
        // through while the flood runs; the vast majority of the 30 must be dropped.
        assertThat(received.size).isBetween(2, 15)
        assertThat(received.sorted()).isEqualTo(received) // Order is preserved.
    }

    @Test
    fun invalidConfiguration_failsFastAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            CapturingFloodAppender(requestPerSecond = 0, burstDurationMillis = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            CapturingFloodAppender(requestPerSecond = 10, burstDurationMillis = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CapturingFloodAppender(requestPerSecond = 10, burstDurationMillis = 100, burstResetPeriodMillis = 50)
        }
    }
}
