package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import io.github.smyrgeorge.log4k.Meter.Instrument.Kind
import io.github.smyrgeorge.log4k.impl.SimpleMeter
import io.github.smyrgeorge.log4k.utils.CapturingMeteringAppender
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Integration tests for [Meter]. Each test registers a [CapturingMeteringAppender] and drives real
 * instruments, then asserts on what actually flowed through the `RootLogger -> Channel -> appender`
 * pipeline (an event only reaches the appender once an instrument emits it and the metering queue is
 * consumed).
 *
 * Delivery is asynchronous, so the tests run inside [runTest] and suspend on `awaitCreate(...)` /
 * `awaitValue(...)` until the event in question has been appended.
 */
class MeterTests {

    private class SampleForMeterFactory

    private lateinit var appender: CapturingMeteringAppender

    // Detach whatever appenders are registered on the global RootLogger, install only our capturing
    // appender for the test, and restore the original set afterwards so tests stay isolated.
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
    fun creatingInstrument_emitsCreateInstrumentEvent() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)

        meter.counter<Long>("orders.created", unit = "1", description = "created orders")

        val created = appender.awaitCreate("orders.created")
        assertThat(created.kind).isEqualTo(Kind.Counter)
        assertThat(created.unit).isEqualTo("1")
        assertThat(created.description).isEqualTo("created orders")
    }

    @Test
    fun counterIncrement_emitsIncrementEventWithValueAndTags() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val counter = meter.counter<Long>("orders.count")

        counter.increment(3L, "region" to "eu")

        val event = appender.awaitValue("orders.count")
        assertThat(event).isInstanceOf(MeteringEvent.Increment::class)
        assertThat(event.value).isEqualTo(3L)
        assertThat(event.tags["region"]).isEqualTo("eu")
    }

    @Test
    fun upDownCounterDecrement_emitsDecrementEvent() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val counter = meter.upDownCounter<Long>("sessions.active")

        counter.decrement(2L)

        val event = appender.awaitValue("sessions.active")
        assertThat(event).isInstanceOf(MeteringEvent.Decrement::class)
        assertThat(event.value).isEqualTo(2L)
    }

    @Test
    fun histogramRecord_emitsRecordEvent() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val histogram = meter.histogram<Double>("request.latency", unit = "ms")

        histogram.record(12.5)

        val event = appender.awaitValue("request.latency")
        assertThat(event).isInstanceOf(MeteringEvent.Record::class)
        assertThat(event.value).isEqualTo(12.5)
    }

    @Test
    fun counter_negativeValue_isRejected() {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val counter = meter.counter<Long>("negative.count")
        assertFailsWith<IllegalStateException> { counter.increment(-1L) }
    }

    @Test
    fun timedMeasure_recordsCallAndDurationAndReturnsResult() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val timed = meter.timed("svc.op")

        val result = timed.measure { 7 }

        assertThat(result).isEqualTo(7)
        val calls = appender.awaitValue("svc.op.calls")
        assertThat(calls).isInstanceOf(MeteringEvent.Increment::class)
        assertThat(calls.value).isEqualTo(1L)
        val duration = appender.awaitValue("svc.op.duration")
        assertThat(duration).isInstanceOf(MeteringEvent.Record::class)
    }

    @Test
    fun timedMeasure_whenBodyThrows_countsErrorAndRethrows() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val timed = meter.timed("svc.fail")
        val boom = IllegalStateException("nope")

        val thrown = assertFailsWith<IllegalStateException> {
            timed.measure<Unit> { throw boom }
        }

        assertThat(thrown.message).isEqualTo("nope")
        val errors = appender.awaitValue("svc.fail.errors")
        assertThat(errors).isInstanceOf(MeteringEvent.Increment::class)
        assertThat(errors.value).isEqualTo(1L)
    }

    @Test
    fun mutedMeter_suppressesInstrumentCreationAndValueEvents() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)

        // While muted, neither the instrument creation nor the increment should emit anything.
        meter.mute()
        val muted = meter.counter<Long>("muted.count")
        muted.increment(5L)

        // After unmuting, a marker instrument does emit — and must be the first event of each kind we
        // see among this test's instruments, proving the muted operations produced nothing before it.
        meter.unmute()
        val marker = meter.counter<Long>("marker.count")
        marker.increment(9L)

        val firstCreate = appender.awaitEvent {
            it is MeteringEvent.CreateInstrument && (it.name == "muted.count" || it.name == "marker.count")
        }
        assertThat(firstCreate.name).isEqualTo("marker.count")

        val firstValue = appender.awaitEvent {
            it is MeteringEvent.ValueEvent && (it.name == "muted.count" || it.name == "marker.count")
        }
        assertThat(firstValue.name).isEqualTo("marker.count")
        assertThat((firstValue as MeteringEvent.ValueEvent).value).isEqualTo(9L)
    }

    // --- Value events: set / record / negative handling ----------------------------------------

    @Test
    fun counterSet_emitsSetEvent() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val counter = meter.counter<Long>("orders.total")

        counter.set(10L)

        val event = appender.awaitValue("orders.total")
        assertThat(event).isInstanceOf(MeteringEvent.Set::class)
        assertThat(event.value).isEqualTo(10L)
    }

    @Test
    fun counterSet_negativeValue_isRejected() {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val counter = meter.counter<Long>("negative.set")
        assertFailsWith<IllegalStateException> { counter.set(-1L) }
    }

    @Test
    fun upDownCounterDecrement_negativeValue_isRejected() {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val counter = meter.upDownCounter<Long>("negative.decrement")
        assertFailsWith<IllegalStateException> { counter.decrement(-1L) }
    }

    @Test
    fun negativeValues_rejectedAcrossNumericTypes() {
        val meter = SimpleMeter("test.meter", Level.INFO)
        assertFailsWith<IllegalStateException> { meter.counter<Int>("neg.int").increment(-1) }
        assertFailsWith<IllegalStateException> { meter.counter<Long>("neg.long").increment(-1L) }
        assertFailsWith<IllegalStateException> { meter.counter<Float>("neg.float").increment(-1.0f) }
        assertFailsWith<IllegalStateException> { meter.counter<Double>("neg.double").increment(-1.0) }
    }

    @Test
    fun histogramRecord_allowsNegativeValues() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val histogram = meter.histogram<Double>("delta") // recorders do not gate negatives

        histogram.record(-3.5)

        val event = appender.awaitValue("delta")
        assertThat(event).isInstanceOf(MeteringEvent.Record::class)
        assertThat(event.value).isEqualTo(-3.5)
    }

    @Test
    fun counterOfInt_carriesIntValue() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)
        val counter = meter.counter<Int>("int.count")

        counter.increment(2)

        val event = appender.awaitValue("int.count")
        assertThat(event.value).isEqualTo(2)
    }

    // --- Instrument kinds & metadata on creation -----------------------------------------------

    @Test
    fun instrumentKinds_areReportedOnCreation() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)

        meter.upDownCounter<Long>("kind.updown")
        meter.gauge<Double>("kind.gauge")
        meter.histogram<Double>("kind.histogram")

        assertThat(appender.awaitCreate("kind.updown").kind).isEqualTo(Kind.UpDownCounter)
        assertThat(appender.awaitCreate("kind.gauge").kind).isEqualTo(Kind.Gauge)
        assertThat(appender.awaitCreate("kind.histogram").kind).isEqualTo(Kind.Histogram)
    }

    @Test
    fun histogramCreation_carriesUnit() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)

        meter.histogram<Double>("latency.unit", unit = "ms")

        assertThat(appender.awaitCreate("latency.unit").unit).isEqualTo("ms")
    }

    // --- timed(...) bundle ---------------------------------------------------------------------

    @Test
    fun timed_cachesBundleByName() {
        val meter = SimpleMeter("test.meter", Level.INFO)
        assertThat(meter.timed("cache.op")).isSameInstanceAs(meter.timed("cache.op"))
        assertThat(meter.timed("cache.op")).isNotSameInstanceAs(meter.timed("cache.other"))
    }

    @Test
    fun timed_createsCallsErrorsAndDurationInstruments() = runTest {
        val meter = SimpleMeter("test.meter", Level.INFO)

        meter.timed("timed.svc")

        assertThat(appender.awaitCreate("timed.svc.calls").kind).isEqualTo(Kind.Counter)
        assertThat(appender.awaitCreate("timed.svc.errors").kind).isEqualTo(Kind.Counter)
        val duration = appender.awaitCreate("timed.svc.duration")
        assertThat(duration.kind).isEqualTo(Kind.Histogram)
        assertThat(duration.unit).isEqualTo("ms")
    }

    // --- Companion factory / registry ----------------------------------------------------------

    @Test
    fun of_byName_returnsSimpleMeter_andCachesByName() {
        val a = Meter.of("test.meter.factory.ByName")
        val b = Meter.of("test.meter.factory.ByName")
        assertThat(a).isInstanceOf(SimpleMeter::class)
        assertThat(a.name).isEqualTo("test.meter.factory.ByName")
        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun of_byClass_cachesInstance() {
        val a = Meter.of(SampleForMeterFactory::class)
        val b = Meter.of(SampleForMeterFactory::class)
        assertThat(a).isSameInstanceAs(b)
        assertThat(a.name).isNotEmpty()
    }
}
