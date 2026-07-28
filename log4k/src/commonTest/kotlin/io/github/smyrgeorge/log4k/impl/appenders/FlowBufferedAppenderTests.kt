package io.github.smyrgeorge.log4k.impl.appenders

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for [FlowBufferedAppender]. Overflow only happens while the consumer is slower than the
 * producer, so [GatedBufferedAppender] blocks inside `handle` until the test hands out permits:
 * the test appends one event, waits until `handle` has entered (pinning the consumer), floods the
 * buffer, and only then releases everything and asserts on what survived.
 */
class FlowBufferedAppenderTests {

    private class GatedBufferedAppender(
        capacity: Int,
        overflow: BufferOverflow? = null, // null -> use the class default (DROP_OLDEST)
    ) : FlowBufferedAppender<Int>(
        capacity = capacity,
        onBufferOverflow = overflow ?: BufferOverflow.DROP_OLDEST,
    ) {
        val entered = Channel<Int>(Channel.UNLIMITED)
        val gate = Channel<Unit>(Channel.UNLIMITED)
        val handled = Channel<Int>(Channel.UNLIMITED)

        override suspend fun handle(event: Int) {
            entered.send(event)
            gate.receive()
            handled.send(event)
        }

        /** Lets [count] pending/future `handle` calls run to completion. */
        suspend fun release(count: Int) = repeat(count) { gate.send(Unit) }
    }

    @Test
    fun fastConsumer_receivesAllEventsInOrder() = runTest {
        // SUSPEND overflow: with a dropping strategy the buffer may legitimately discard events
        // while the collector is still starting up, so only SUSPEND guarantees full delivery.
        val appender = GatedBufferedAppender(capacity = 2, overflow = BufferOverflow.SUSPEND)
        appender.release(10) // Pre-approve everything: the consumer is never behind.
        repeat(10) { appender.append(it + 1) }
        val received = List(10) { appender.handled.receive() }
        assertThat(received).isEqualTo((1..10).toList())
    }

    @Test
    fun overflowWithDropOldest_keepsOnlyTheNewestEvents() = runTest {
        val appender = GatedBufferedAppender(capacity = 2) // Default overflow: DROP_OLDEST.
        appender.append(1)
        assertThat(appender.entered.receive()).isEqualTo(1) // Consumer is now pinned inside handle(1).
        (2..10).forEach { appender.append(it) }
        // Real time for the pipeline to move 2..10 into the (overflowing) buffer.
        withContext(Dispatchers.Default) { delay(200.milliseconds) }
        appender.release(10)

        // 1 was in-flight; of 2..10 only the newest `capacity` events may survive.
        assertThat(appender.handled.receive()).isEqualTo(1)
        assertThat(appender.handled.receive()).isEqualTo(9)
        assertThat(appender.handled.receive()).isEqualTo(10)
        withContext(Dispatchers.Default) { delay(100.milliseconds) }
        assertThat(appender.handled.tryReceive().getOrNull()).isNull()
    }

    @Test
    fun overflowWithDropLatest_keepsOnlyTheOldestEvents() = runTest {
        val appender = GatedBufferedAppender(capacity = 2, overflow = BufferOverflow.DROP_LATEST)
        appender.append(1)
        assertThat(appender.entered.receive()).isEqualTo(1)
        (2..10).forEach { appender.append(it) }
        withContext(Dispatchers.Default) { delay(200.milliseconds) }
        appender.release(10)

        // 1 was in-flight; the buffer kept 2 and 3, everything after was dropped.
        assertThat(appender.handled.receive()).isEqualTo(1)
        assertThat(appender.handled.receive()).isEqualTo(2)
        assertThat(appender.handled.receive()).isEqualTo(3)
        withContext(Dispatchers.Default) { delay(100.milliseconds) }
        assertThat(appender.handled.tryReceive().getOrNull()).isNull()
    }

    @Test
    fun overflowWithSuspend_dropsNothing() = runTest {
        val appender = GatedBufferedAppender(capacity = 2, overflow = BufferOverflow.SUSPEND)
        appender.append(1)
        assertThat(appender.entered.receive()).isEqualTo(1)
        (2..10).forEach { appender.append(it) }
        withContext(Dispatchers.Default) { delay(200.milliseconds) }
        appender.release(10)

        val received = List(10) { appender.handled.receive() }
        assertThat(received).isEqualTo((1..10).toList())
    }
}
