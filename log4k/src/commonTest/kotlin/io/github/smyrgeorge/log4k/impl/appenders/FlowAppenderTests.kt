package io.github.smyrgeorge.log4k.impl.appenders

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Tests for [FlowAppender], driven through small concrete subclasses. The appender processes
 * events on a real background dispatcher (not the test scheduler), so each test appends events
 * and then suspends on an unlimited capture channel until the pipeline delivers them — the same
 * pattern used by `CapturingLoggingAppender`.
 */
class FlowAppenderTests {

    /** Identity pipeline: whatever is appended must reach [handle] unchanged. */
    private class CapturingFlowAppender : FlowAppender<Int, Int>() {
        val handled = Channel<Int>(Channel.UNLIMITED)
        override fun setup(flow: Flow<Int>): Flow<Int> = flow
        override suspend fun handle(event: Int) {
            handled.send(event)
        }
    }

    /** Fails on one specific event to prove a throwing [handle] does not kill the pipeline. */
    private class FailingFlowAppender : FlowAppender<Int, Int>() {
        val handled = Channel<Int>(Channel.UNLIMITED)
        override fun setup(flow: Flow<Int>): Flow<Int> = flow
        override suspend fun handle(event: Int) {
            if (event == 2) error("boom")
            handled.send(event)
        }
    }

    /** Uses [setup] to transform events, exercising the `T != E` case. */
    private class MappingFlowAppender : FlowAppender<String, Int>() {
        val handled = Channel<String>(Channel.UNLIMITED)
        override fun setup(flow: Flow<Int>): Flow<String> = flow.map { "#$it" }
        override suspend fun handle(event: String) {
            handled.send(event)
        }
    }

    @Test
    fun append_deliversEventToHandle() = runTest {
        val appender = CapturingFlowAppender()
        appender.append(42)
        assertThat(appender.handled.receive()).isEqualTo(42)
    }

    @Test
    fun append_preservesEventOrder() = runTest {
        val appender = CapturingFlowAppender()
        repeat(100) { appender.append(it) }
        val received = List(100) { appender.handled.receive() }
        assertThat(received).isEqualTo((0 until 100).toList())
    }

    @Test
    fun setup_canTransformEvents() = runTest {
        val appender = MappingFlowAppender()
        appender.append(1)
        appender.append(2)
        assertThat(appender.handled.receive()).isEqualTo("#1")
        assertThat(appender.handled.receive()).isEqualTo("#2")
    }

    @Test
    fun handleFailure_doesNotStopProcessingSubsequentEvents() = runTest {
        val appender = FailingFlowAppender()
        appender.append(1)
        appender.append(2) // Throws inside handle; must be swallowed by the pipeline.
        appender.append(3)
        assertThat(appender.handled.receive()).isEqualTo(1)
        assertThat(appender.handled.receive()).isEqualTo(3)
    }

    @Test
    fun name_isDerivedFromTheConcreteClass() {
        val appender = CapturingFlowAppender()
        assertThat(appender.name).contains("CapturingFlowAppender")
    }

    /** A subclass whose [setup] fails, e.g. because of an invalid configuration. */
    private class BrokenSetupAppender : FlowAppender<Int, Int>() {
        override fun setup(flow: Flow<Int>): Flow<Int> = error("invalid configuration")
        override suspend fun handle(event: Int) = Unit
    }

    @Test
    fun setupFailure_surfacesAtTheFirstAppend_insteadOfDyingSilently() = runTest {
        val appender = BrokenSetupAppender()
        assertFailsWith<IllegalStateException> { appender.append(1) }
    }
}
