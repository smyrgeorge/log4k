package io.github.smyrgeorge.log4k.impl.extensions

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Tests for the [preventFloodingWithBurst] rate limiter. Time is fully faked through the
 * `nowMillis` hook: the source flow advances a virtual `now` variable between emissions, so
 * every scenario is deterministic — no sleeps, no real clocks.
 */
class FlowPreventFloodingTests {

    /** Collects the drop reports as (dropped, cumulativeTotal) pairs. */
    private class DropLog {
        val reports = mutableListOf<Pair<Int, Long>>()
        val log: (Int, Long) -> Unit = { d, t -> reports.add(d to t) }
    }

    @Test
    fun eventsUnderTheRate_allPass() = runTest {
        var now = 0L
        val drops = DropLog()
        // rps=10 -> 100ms window; events spaced exactly at the window boundary.
        val out = flow {
            repeat(5) {
                emit(it)
                now += 100
            }
        }.preventFloodingWithBurst(10, 50, 5000, { now }, drops.log).toList()

        assertThat(out).isEqualTo(listOf(0, 1, 2, 3, 4))
        assertThat(drops.reports).isEmpty()
    }

    @Test
    fun burst_allowsFastEvents_untilTheBurstWindowCloses() = runTest {
        var now = 0L
        val drops = DropLog()
        // rps=10 -> 100ms window; burst window 200ms. Events every 10ms from t=0 to t=300.
        // t=0 passes normally; t=10 opens the burst; everything until t=210 (burst start + 200)
        // is emitted; the 9 events at t=220..300 are dropped and reported on completion.
        val out = flow {
            repeat(31) {
                emit(it)
                now += 10
            }
        }.preventFloodingWithBurst(10, 200, 5000, { now }, drops.log).toList()

        assertThat(out).isEqualTo((0..21).toList())
        assertThat(drops.reports).isEqualTo(listOf(9 to 9L))
    }

    @Test
    fun drops_areReportedOnTheNextEmission_withCumulativeTotal() = runTest {
        var now = 0L
        val drops = DropLog()
        val out = flow {
            suspend fun at(time: Long, value: Int) {
                now = time
                emit(value)
            }
            at(0, 0)    // Passes (first event).
            at(10, 1)   // Opens the 50ms burst, emitted.
            at(70, 2)   // Burst over (70-10 > 50), dropped.
            at(80, 3)   // Dropped.
            at(200, 4)  // 190ms since last emission -> passes; reports 2 drops.
            at(210, 5)  // Fast again, burst not reset yet (5000ms) -> dropped.
            at(400, 6)  // Passes; reports 1 more drop, total 3.
        }.preventFloodingWithBurst(10, 50, 5000, { now }, drops.log).toList()

        assertThat(out).isEqualTo(listOf(0, 1, 4, 6))
        assertThat(drops.reports).isEqualTo(listOf(2 to 2L, 1 to 3L))
    }

    @Test
    fun burst_isNotReopened_beforeTheResetPeriod_andReopensAfterIt() = runTest {
        var now = 0L
        val drops = DropLog()
        val out = flow {
            suspend fun at(time: Long, value: Int) {
                now = time
                emit(value)
            }
            at(0, 0)     // Passes.
            at(10, 1)    // Opens the 50ms burst, emitted.
            at(80, 2)    // Inside the 100ms window but the burst is over (70 > 50) -> dropped.
            at(300, 3)   // Passes; reset period (1000ms since burst start) not reached.
            at(310, 4)   // Fast; still no new burst allowed -> dropped.
            at(1500, 5)  // Passes; burst state resets (1490 > 1000).
            at(1510, 6)  // Fast; a NEW burst opens -> emitted.
        }.preventFloodingWithBurst(10, 50, 1000, { now }, drops.log).toList()

        assertThat(out).isEqualTo(listOf(0, 1, 3, 5, 6))
        assertThat(drops.reports).isEqualTo(listOf(1 to 1L, 1 to 2L))
    }

    @Test
    fun eachCollection_startsWithFreshState() = runTest {
        var now = 0L
        val drops = DropLog()
        val limited = flow {
            now = 0
            emit(1)  // Passes.
            now = 10
            emit(2)  // Opens the 50ms burst, emitted.
            now = 70
            emit(3)  // Burst over, dropped.
        }.preventFloodingWithBurst(10, 50, 5000, { now }, drops.log)

        assertThat(limited.toList()).isEqualTo(listOf(1, 2))
        assertThat(limited.toList()).isEqualTo(listOf(1, 2))
        // A leaked totalDropped would make the second report (1, 2).
        assertThat(drops.reports).isEqualTo(listOf(1 to 1L, 1 to 1L))
    }

    @Test
    fun ratesAboveOneThousandPerSecond_stillLimit() = runTest {
        var now = 0L
        val drops = DropLog()
        // With the old zero-width window (1000/5000 = 0) nothing was ever dropped.
        val out = flow {
            suspend fun at(time: Long, value: Int) {
                now = time
                emit(value)
            }
            at(0, 0)  // Passes.
            at(0, 1)  // Same millisecond -> opens the 1ms burst, emitted.
            at(5, 2)  // Passes (5ms gap >= 1ms window).
            at(5, 3)  // Same millisecond, burst over (5 > 1) -> dropped.
        }.preventFloodingWithBurst(5000, 1, 5000, { now }, drops.log).toList()

        assertThat(out).isEqualTo(listOf(0, 1, 2))
        assertThat(drops.reports).isEqualTo(listOf(1 to 1L))
    }

    @Test
    fun invalidArguments_throw() {
        val flow: Flow<Int> = flow {}
        assertFailsWith<IllegalArgumentException> { flow.preventFloodingWithBurst(0, 100, 5000) }
        assertFailsWith<IllegalArgumentException> { flow.preventFloodingWithBurst(10, 0, 5000) }
        // A reset period shorter than the burst itself would allow bursts to chain indefinitely.
        assertFailsWith<IllegalArgumentException> { flow.preventFloodingWithBurst(10, 100, 50) }
    }
}
