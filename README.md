# log4k

![Build](https://github.com/smyrgeorge/log4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/log4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/log4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/log4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/log4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue.svg?logo=kotlin)](http://kotlinlang.org)

The missing logger for Kotlin Multiplatform.

This project aims to develop a logger designed for Kotlin Multiplatform
that operates asynchronously and is event-driven at its core.
Built from the ground up, log4k leverages Kotlin’s coroutines
and channels to deliver efficient and scalable logging.

> [!IMPORTANT]  
> The project is in a very early stage; thus, breaking changes should be expected.

📖 [Documentation](https://smyrgeorge.github.io/log4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## TODO

- [ ] Introduce tracing API (event/span)
- [ ] `CoroutineContexAwareLogger`: `Logger` that will collect more info from the coroutine context. (in progress)
- [ ] Ability to chain appenders (in progress)
- [ ] Json console logger
- [ ] `LogbackAppender`: `Appender` that will publish the logging events to the logback.

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

## API

```kotlin
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

## Examples

```kotlin
// Create a Logger.
private val log: Logger = Logger.of(this::class)

log.debug("ignore")
log.debug { "ignore + ${5}" } // Will be evaluated only if DEBUG logs are enabled.
log.info("this is a test")

// Support for mute/unmute each logger programmatically.
RootLogger.loggers.mute("io.github.smyrgeorge.log4k.MainTests")
log.info("this is a test with 1 arg: {}", "hello")
RootLogger.loggers.unmute(this::class) // Will set the logging level that had before was muted.
log.info("this is a test with 1 arg: {}", "hello")

try {
    error("An error occurred!")
} catch (e: Exception) {
    log.error(e.message)
    log.error(e.message, e)
}

// Create custom appenders.
// See [BatchAppender] for more information.
class MyBatchAppender(size: Int) : BatchAppender(size) {
    override suspend fun append(event: List<LoggingEvent>) {
        // E.g. send batch over http.
        // In this case every [append] method will be called every 5 elements.
        println(event.joinToString { it.message })
    }
}

runBlocking {
    delay(2000)
    val appender = MyBatchAppender(5)
    // Register the appender.
    RootLogger.appenders.register(appender)

    // Will print:
    // 0, 1, 2, 3, 4
    // 5, 6, 7, 8, 9
    repeat(10) {
        log.info("$it")
        delay(500)
    }

    delay(2000)
}
```