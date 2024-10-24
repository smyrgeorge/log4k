# log4k

![Build](https://github.com/smyrgeorge/log4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/log4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/log4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/log4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/log4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue.svg?logo=kotlin)](http://kotlinlang.org)

A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.

This project provides a robust, event-driven logging and tracing platform specifically designed for Kotlin
Multiplatform (also compatible with the Java ecosystem). Built with coroutines and channels at its core, it offers
asynchronous, scalable logging across multiple platforms.

This project also tries to be fully compatible with `OpenTelemetry` standard.

> [!IMPORTANT]  
> The project is in a very early stage; thus, breaking changes should be expected.

📖 [Documentation](https://smyrgeorge.github.io/log4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## TODO

- [ ] Support for OpenTelemetry's Metrics
- [ ] `CoroutineContexAwareLogger`: `Logger` that will collect more info from the coroutine context.
- [ ] Ability to chain appenders
- [ ] Json console logger
- [ ] `LogbackAppender`: `Appender` that will publish the logging events to the logback.

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
// 2024-10-24T07:19:38.295050Z [native-13] - WARN  FlowFloodProtectedAppender - Dropped 6556 log messages due to flooding (total: 987299).
// 995897 2024-10-24T07:19:38.314454Z [native-1] - INFO  Main - 995782
// 2024-10-24T07:19:38.315134Z [native-19] - WARN  FlowFloodProtectedAppender - Dropped 4557 log messages due to flooding (total: 991856).
```

To tackle similar issues, we can apply dynamic rate-limiting based on system load or log severity, prioritizing critical
logs while dropping less important ones during high-traffic periods. Batching or buffering logs can also help optimize
processing, ensuring important logs are preserved without overwhelming the system. This reduces costs and maintains log
integrity.

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

## Tracing API

The tracing API is fully compatible with the `OpenTelemetry` standard, enabling seamless distributed tracing, metric
collection, and context propagation across services.

```kotlin
private val trace: Tracer = Tracer.of(this::class)
// We need to manually register an appender.
// The [SimpleConsoleTracingAppender] will print the traces in the console
// (is just an example, should not be used as a real example).
RootLogger.Tracing.register(SimpleConsoleTracingAppender())

// Create the span and then start it.
val span: TracingEvent.Span = trace.span("test").start()
span.event(name = "test-event")
// Close the span manually.
span.end()
```

Similarly to the logging API, we also support a more kotlin style API:

```kotlin
// Create the parent span.
// NOTICE: we do not start it, since it's already started.
val parent: TracingEvent.Span = trace.span(id = "PARENT_SPAN_ID", traceId = "TRACE_ID", name = "parent")
// Starts immediately the span.
trace.span("test", parent) {
    // Set span attributes.
    it.attributes["key"] = "value"
    // Send events that are related to the current span.
    it.event(name = "event-1", level = Level.DEBUG)
    it.debug(name = "event-1") // Same as event(name = "event-1", level = Level.DEBUG)
    // Include attributes in the event.
    it.event(name = "event-2", attributes = mapOf("key" to "value"))
    it.event(name = "event-2") { attrs: MutableMap<String, Any?> ->
        attrs["key"] = "value"
    }
    // Automatically closes at the end of te scope.
}
```

## Examples

For more detailed examples take also a look at the `examples` module.

```kotlin
// Create a Logger.
private val log: Logger = Logger.of(this::class)
private val trace: Tracer = Tracer.of(this::class)
RootLogger.Tracing.register(SimpleConsoleTracingAppender())

log.debug("ignore")
log.debug { "ignore + ${5}" } // Will be evaluated only if DEBUG logs are enabled.
log.info("this is a test")

// Support for mute/unmute each logger programmatically.
// The [RootLogger] maintains a list with all registered loggers.
RootLogger.Logging.logges.mute("io.github.smyrgeorge.log4k.MainTests")
log.info("this is a test with 1 arg: {}", "hello")
log.unmute() // Will set the logging level that had before was muted.
log.info("this is a test with 1 arg: {}", "hello")

try {
    error("An error occurred!")
} catch (e: Exception) {
    log.error(e.message)
    log.error(e) { e.message }
}

// Create custom appenders.
// See [BatchAppender] for more information.
class MyBatchAppender(size: Int) : BatchAppender<LoggingEvent>(size) {
    override suspend fun handle(event: List<LoggingEvent>) {
        // E.g. send batch over http.
        // In this case every [append] method will be called every 5 elements.
        println(event.joinToString { it.message })
    }
}

val appender = MyBatchAppender(5)
// Register the appender.
RootLogger.Logging.appenders.register(appender)

// Will print:
// 0, 1, 2, 3, 4
// 5, 6, 7, 8, 9
repeat(10) {
    log.info("$it")
    delay(500)
}

// Starts immediately the span.
trace.span("test") {
    log.info(it, "this is a test with span") // The log will contain the span id.
    // Set span attributes.
    it.attributes["key"] = "value"
    // Send events that are related to the current span.
    it.event(name = "event-1", level = Level.DEBUG)
    // Include attributes in the event.
    it.event(name = "event-2", attributes = mapOf("key" to "value"))
    it.event(name = "event-2") { attrs: MutableMap<String, Any?> ->
        attrs["key"] = "value"
    }
    // Automatically closes at the end of te scope.
}

// Create the span and then start it.
val span: TracingEvent.Span = trace.span("test").start()
span.event("this is a test event")
// Close the span manually.
span.end()
```