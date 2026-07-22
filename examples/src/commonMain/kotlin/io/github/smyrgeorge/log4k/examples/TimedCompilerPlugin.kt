package io.github.smyrgeorge.log4k.examples

import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.annotation.Timed
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleMeteringCollectorAppender
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object TimedCompilerPlugin {

    // Class-level @Timed: every eligible public member is instrumented with call/error/duration metrics.
    // `OrderService` declares no `meter`, so the plugin synthesizes `private val _meter_ = Meter.of(this::class)`.
    @Timed
    class OrderService {
        fun placeOrder(id: Long): String = "order-$id" // -> "OrderService.placeOrder.{calls,duration}"

        @Timed(name = "orders.checkout") // per-function override of the metric base name.
        fun checkout(total: Double): Double = total * 1.24
    }

    class PaymentService {
        // suspend is supported (the wrapper reuses the `inline` Meter.Timed.measure helper).
        @Timed
        suspend fun charge(amount: Double): Double {
            delay(10.milliseconds)
            return amount
        }

        // Exception path: increments the error counter (and still records the duration), then rethrows.
        @Timed(name = "payments.refund")
        fun refund(): Nothing = error("declined")
    }

    class ReportService {
        // Declared by the user and reused by the plugin (no `_meter_` field is synthesized).
        @Suppress("unused")
        private val meter = Meter.of(this::class)

        @Timed
        fun generate(kind: String): String = "report-$kind"
    }

    fun run() = runBlocking {
        RootLogger.Metering.appenders.unregisterAll()
        RootLogger.Metering.appenders.register(SimpleMeteringCollectorAppender())
        val collector = RootLogger.Metering.appenders.get(SimpleMeteringCollectorAppender::class)

        val orders = OrderService()
        repeat(3) { orders.placeOrder(it.toLong()) }
        orders.checkout(100.0)

        val payments = PaymentService()
        payments.charge(42.0)
        payments.charge(7.0)
        try {
            payments.refund()
        } catch (e: IllegalStateException) {
            println(">> caught rethrown exception: ${e.message}")
        }

        // `ReportService` declares its own `meter`, which the plugin reuses (no `_meter_` synthesized).
        val reports = ReportService()
        reports.generate("daily")

        // Give the async metering appender time to process the events.
        delay(1.seconds)
        println("=== OpenMetrics ===")
        println(collector.toOpenMetricsLineFormatString())
    }
}
