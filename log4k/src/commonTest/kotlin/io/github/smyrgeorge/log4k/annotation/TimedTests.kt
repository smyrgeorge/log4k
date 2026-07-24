package io.github.smyrgeorge.log4k.annotation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.utils.CapturingMeteringAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

// --- Fixtures instrumented by the log4k-compiler-plugin (wired onto the test compilations) --------

@Timed
private class TimedFixture {
    fun placeOrder(id: Long): String = "order-$id" // "TimedFixture.placeOrder.{calls,duration}"

    @Timed(name = "orders.checkout")
    fun checkout(total: Double): Double = total * 1.1 // custom base name

    @NoTime
    fun quote(total: Double): Double = total // opted out

    fun boom(): Nothing = error("declined") // error path -> ".errors" counter + rethrow
}

@NoTime
private class SilencedTimedFixture {
    @Timed
    fun ping(): String = "pong" // class-level @NoTime kill switch -> not measured despite @Timed
}

/**
 * End-to-end tests for the [Timed] / [NoTime] annotations. The compiler plugin is applied to the test
 * compilations, so the fixtures above are really instrumented; each test drives a fixture and asserts
 * on the metering events that flowed through the `RootLogger -> Channel -> appender` pipeline.
 */
class TimedTests {

    private lateinit var appender: CapturingMeteringAppender
    private var saved: List<Appender<MeteringEvent>> = emptyList()

    @BeforeTest
    fun setup() {
        saved = RootLogger.Metering.appenders.all()
        RootLogger.Metering.appenders.unregisterAll()
        appender = CapturingMeteringAppender()
        RootLogger.Metering.appenders.register(appender)
    }

    @AfterTest
    fun teardown() {
        RootLogger.Metering.appenders.unregisterAll()
        saved.forEach { RootLogger.Metering.appenders.register(it) }
    }

    @Test
    fun classLevelTimed_recordsCallAndDuration() = runTest {
        val result = TimedFixture().placeOrder(1)

        assertThat(result).isEqualTo("order-1")
        val calls = appender.awaitValue("TimedFixture.placeOrder.calls")
        assertThat(calls).isInstanceOf(MeteringEvent.Increment::class)
        assertThat(calls.value).isEqualTo(1L)
        val duration = appender.awaitValue("TimedFixture.placeOrder.duration")
        assertThat(duration).isInstanceOf(MeteringEvent.Record::class)
    }

    @Test
    fun timed_explicitName_usesThatBaseName() = runTest {
        TimedFixture().checkout(100.0)

        val calls = appender.awaitValue("orders.checkout.calls")
        assertThat(calls).isInstanceOf(MeteringEvent.Increment::class)
    }

    @Test
    fun timed_errorPath_incrementsErrorCounterAndRethrows() = runTest {
        val thrown = assertFailsWith<IllegalStateException> { TimedFixture().boom() }

        assertThat(thrown.message).isEqualTo("declined")
        val errors = appender.awaitValue("TimedFixture.boom.errors")
        assertThat(errors).isInstanceOf(MeteringEvent.Increment::class)
        assertThat(errors.value).isEqualTo(1L)
    }

    @Test
    fun noTime_onFunction_optsOut() = runTest {
        val fixture = TimedFixture()
        fixture.quote(1.0)     // @NoTime -> no metrics
        fixture.placeOrder(2)  // marker

        val first = appender.awaitEvent {
            it is MeteringEvent.ValueEvent &&
                (it.name == "TimedFixture.quote.calls" || it.name == "TimedFixture.placeOrder.calls")
        }
        assertThat(first.name).isEqualTo("TimedFixture.placeOrder.calls")
    }

    @Test
    fun noTime_onClass_disablesEverything() = runTest {
        SilencedTimedFixture().ping() // class @NoTime -> nothing, even though method is @Timed
        TimedFixture().placeOrder(3)  // marker

        val first = appender.awaitEvent {
            it is MeteringEvent.ValueEvent &&
                (it.name == "SilencedTimedFixture.ping.calls" || it.name == "TimedFixture.placeOrder.calls")
        }
        assertThat(first.name).isEqualTo("TimedFixture.placeOrder.calls")
    }
}
