# log4k

![Build](https://github.com/smyrgeorge/log4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/log4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/log4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/log4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/log4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

![](https://img.shields.io/static/v1?label=&message=Platforms&color=grey)
![](https://img.shields.io/static/v1?label=&message=Jvm&color=blue)
![](https://img.shields.io/static/v1?label=&message=Linux&color=blue)
![](https://img.shields.io/static/v1?label=&message=macOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Windows&color=blue)
![](https://img.shields.io/static/v1?label=&message=iOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Android&color=blue)
![](https://img.shields.io/static/v1?label=&message=WasmJs&color=blue)
![](https://img.shields.io/static/v1?label=&message=WasmWasi&color=blue)

A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.

This project provides a robust, event-driven logging and tracing platform specifically designed for Kotlin
Multiplatform (also compatible with the Java ecosystem). Built with coroutines and channels at its core, it offers
asynchronous, scalable logging across multiple platforms.

This project also tries to be fully compatible with `OpenTelemetry` standard.

> [!IMPORTANT]  
> The project is in a very early stage; thus, breaking changes should be expected.

📖 [Documentation](https://smyrgeorge.github.io/log4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## Usage

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // https://central.sonatype.com/artifact/io.github.smyrgeorge/log4k
    implementation("io.github.smyrgeorge:log4k:x.y.z")
}
```

## Architecture

<!--suppress HtmlDeprecatedAttribute -->
<p align="center">
  <!--suppress CheckImageSize -->
<img src="img/arch.png" alt="Architecture" width="158" height="338">
</p>

At the core of the logging system is the `RootLogger`, which manages a `Channel<LoggingEvent>`. All logging events are
enqueued in this channel, and the `RootLogger` is responsible for distributing them to the registered appenders (refer
to `RootLogger` for more details).

Each `Appender` may also maintain its own `Channel`, which is particularly beneficial in scenarios that require
batching—such as sending batched log or trace events over the network or appending them to a file. For instance, the
`FlowAppender` leverages `kotlinx.coroutines.flow.Flow` to process incoming events efficiently.

On the other hand, some appenders can be simpler and do not require a `Channel` for event processing. For example, the
`ConsoleAppender` directly prints each incoming event to the console without queuing, offering a straightforward logging
solution.

The tracing module shares exactly the same principals.

### Prevent log/trace flooding.

_Log rate spikes are common and often go unnoticed. They could be an indication that something went terribly wrong or
that a high-traffic system was unintentionally configured with verbose logging._

At times, it's crucial to reduce the volume of logs and traces to prevent unnecessary costs. In our solution, we can
leverage Kotlin's Flow to manage log streams efficiently by dropping excess log messages when needed. For example, the
`FlowFloodProtectedAppender` is designed specifically for this scenario. It not only limits the flood of log messages
but also reports the number of dropped messages, giving you visibility into how much data is being filtered out.

```kotlin
class SimpleFloodProtectedAppender(
    requestPerSecond: Int,
    burstDurationMillis: Int
) : FlowFloodProtectedAppender<LoggingEvent>(requestPerSecond, burstDurationMillis) {
    override suspend fun handle(event: LoggingEvent) = event.print()
}

RootLogger.Logging.appenders.unregisterAll()
RootLogger.Logging.appenders.register(
    SimpleFloodProtectedAppender(requestPerSecond = 50, burstDurationMillis = 100)
)

repeat(1_000_000) {
    log.info("$it")
}

// The above will produce the following output:
// 115 2024-10-24T07:19:34.707789Z [native-1] - INFO  Main - 0
// 116 2024-10-24T07:19:34.707869Z [native-1] - INFO  Main - 1
// 117 2024-10-24T07:19:34.707884Z [native-1] - INFO  Main - 2
// 118 2024-10-24T07:19:34.707899Z [native-1] - INFO  Main - 3
// # ...
// # After some ~4k logs starts to drop.
// 991339 2024-10-24T07:19:38.294933Z [native-1] - INFO  Main - 991224
// 2024-10-24T07:19:38.295050Z [native-13] - WARN  FlowFloodProtectedAppender - Dropped 6556 log messages due to flooding (total dropped: 987299).
// 995897 2024-10-24T07:19:38.314454Z [native-1] - INFO  Main - 995782
// 2024-10-24T07:19:38.315134Z [native-19] - WARN  FlowFloodProtectedAppender - Dropped 4557 log messages due to flooding (total dropped: 991856).
```

To tackle similar issues, we can apply dynamic rate-limiting based on system load or log severity, prioritizing critical
logs while dropping less important ones during high-traffic periods. Batching or buffering logs can also help optimize
processing, ensuring important logs are preserved without overwhelming the system. This reduces costs and maintains log
integrity.

## Coroutines

For detailed setup instructions and usage, see the project’s [README.md](./log4k-coroutines/README.md)

## Logging API

By default, the `SimpleConsoleLoggingAppender` is automatically registered.
You can change the behaviour by executing, early in your program, the following:

```kotlin
RootLogger.Logging.appenders.unregister(SimpleConsoleLoggingAppender::class)
```

```kotlin
// Create a Logger.
private val log: Logger = Logger.of(this::class)

log.info("this is test log")
log.info("this is test with 1 arg: {}", "hello")
log.error(e.message, e)
```

We also support a more kotlin style API:

```kotlin
log.debug { "ignore" }
log.debug { "ignore + ${5}" } // Will be evaluated only if DEBUG logs are enabled.
log.error { e.message }
log.error(e) { e.message } // e: Throwable
```

### Json Appender

```kotlin
// You can register the `SimpleJsonConsoleLoggingAppender` for json logs in the console.
RootLogger.Logging.appenders.register(SimpleJsonConsoleLoggingAppender())
```

### Full SLF4J Integration Supported

We’ve ensured complete compatibility with SLF4J, allowing seamless integration into projects that already use SLF4J as a
logging abstraction layer. By providing SLF4J support, `log4k` can be easily adopted in both new and existing
applications
without requiring significant changes to your current logging setup. This means you can leverage log4k’s powerful,
multiplatform capabilities while maintaining compatibility with other SLF4J-compatible libraries and frameworks.

To enable SLF4J integration, simply add the following dependency to your project:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // https://central.sonatype.com/artifact/io.github.smyrgeorge/log4k-slf4j
    implementation("io.github.smyrgeorge:log4k-slf4j:x.y.z")
}
```

For detailed setup instructions and usage, see the project’s [README.md](./log4k-slf4j/README.md)

## Tracing API

The tracing API is fully compatible with the `OpenTelemetry` standard, enabling seamless distributed tracing, metric
collection, and context propagation across services.

```kotlin
private val trace: Tracer = Tracer.of(this::class)
// We need to manually register an appender.
// The [SimpleConsoleTracingAppender] will print the traces in the console
// (is just an example, should not be used as a real example).
RootLogger.Tracing.appenders.register(SimpleConsoleTracingAppender())

// Create the span and then start it.
val span: TracingEvent.Span.Local = trace.span("test").start()
span.event(name = "test-event")
// Close the span manually.
span.end()
```

Similarly to the logging API, we also support a more kotlin style API:

```kotlin
trace.span("test", parent) {
    log.info(this, "this is a test with span") // The log will contain the span id.
    // Set span tags.
    tags["key"] = "value"
    // Send events that are related to the current span.
    event(name = "event-1", level = Level.DEBUG)
    debug(name = "event-1") // Same as event(name = "event-1", level = Level.DEBUG)
    // Include tags in the event.
    event(name = "event-2", tags = mapOf("key" to "value"))
    event(name = "event-2") { tags ->
        tags["key"] = "value"
    }
    // Nested Span.
    span("test-2") {
        event(name = "event-3", tags = mapOf("key" to "value"))
        log.info(this, "this is a test with span") // The log will contain the span id.
    }
    // Automatically closes at the end of te scope.
}
```

Additionally, you can instantiate a span that represents the parent span.
This is useful in cases that the parent span is created outside our application (e.g. received from an HTTP call).

```kotlin
// Create the parent span.
// NOTICE: we do not start it, since it's already started.
val parent: TracingEvent.Span.Remote = trace.span(id = "ID_EXAMPLE", traceId = "TRACE_ID_EXAMPLE")
trace.span("test", parent) {
    // Your logic here
}
```

In the examples above, we see two variations of the `Span` class:

- **Span.Local**: Represents a span created locally within our application, exposing all methods such as `start`, `end`,
  `event`, `debug`, `info`, and more.
- **Span.Remote**: Represents a span created outside our application and propagated to us (e.g., from an HTTP call). It
  does not expose any methods and serves only as a reference to the parent remote span.

## Metering API

A measurement captured at runtime.

A metric is a measurement of a service captured at runtime. The moment of capturing a measurements is known as a metric
event, which consists not only of the measurement itself, but also the time at which it was captured and associated
metadata.

Several types of metrics are supported:

- **Counter**: A value that accumulates over time – you can think of this like an odometer on a car; it only ever goes
  up.
- **UpDownCounter**: A value that accumulates over time, but can also go down again. An example could be a queue length,
  it will increase and decrease with the number of work items in the queue.
- **Gauge**: Measures a current value at the time it is read. An example would be the fuel gauge in a vehicle. Gauges
  are asynchronous.
- **Histogram** (in progress): A client-side aggregation of values, such as request latencies. A histogram is a good
  choice if you are interested in value statistics. For example: How many requests take fewer than 1s?

```kotlin
// Create a Counter that holds Int values.
val c1 = meter.counter<Int>("event-a")
delay(1000)
c1.increment(1, "label" to "pool-a")
c1.increment(1, "label" to "pool-a")

// Create a UpDownCounter that holds Double values.
val c2 = meter.upDownCounter<Double>("event-b")
delay(1000)
c2.increment(2.0, "label" to "pool-b")
c2.increment(2.0, "label" to "pool-b")
c2.decrement(2.0, "label" to "pool-b")

// Create a Gauge
val g1 = meter.gauge<Int>("thread-pool-size")
delay(1000)
g1.record(3, "pool" to "pool-a")
g1.record(6, "pool" to "pool-b")
```

Each time an operation is performed (i.e., a measurement is taken with a meter), an event is triggered and propagated to
all registered appenders. For instance, we can register the `SimpleMeteringCollectorAppender` appender:

```kotlin
val collector = SimpleMeteringCollectorAppender()
RootLogger.Metering.appenders.register(collector)
```

The `SimpleMeteringCollectorAppender` processes all events, updating the value for each registered instrument. It also
provides a method that returns a string with the collected data in the `OpenMetrics` line format.

```kotlin
val metrics = collector.toOpenMetricsLineFormatString()
println(metrics)

// The above example will print:
//
// # HELP event-a
// # TYPE event-a counter
// event-a {label="pool-a"} 2 1730360802506
//
// # HELP thread-pool-size
// # TYPE thread-pool-size gauge
// thread-pool-size {pool="pool-a"} 3 1730360802506
// thread-pool-size {pool="pool-b"} 6 1730360802506
//
// # HELP event-b
// # TYPE event-b updowncounter
// event-b {label="pool-b"} 4.0 1730360802506
```

### Gauge

We also provide a convenient way to periodically poll and publish value changes, enabling automated and timely updates.
This approach ensures that values are recorded consistently, which is particularly useful for monitoring changes over
time and minimizing manual intervention.

```kotlin
meter.gauge<Int>("thread-pool-size").poll(every = 10.seconds) {
    record(3, "pool" to "pool-a")
    record(6, "pool" to "pool-b")
}
```

Using this method, values are automatically recorded at regular intervals, making it ideal for tracking metrics in
dynamic environments.

## Examples

For more detailed examples take also a look at the `examples` module.