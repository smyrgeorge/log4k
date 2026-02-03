# log4k-coroutines (Deprecated)

![Build](https://github.com/smyrgeorge/log4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/log4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/log4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/log4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/log4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue.svg?logo=kotlin)](http://kotlinlang.org)

![](https://img.shields.io/static/v1?label=&message=Platforms&color=grey)
![](https://img.shields.io/static/v1?label=&message=Jvm&color=blue)
![](https://img.shields.io/static/v1?label=&message=Linux&color=blue)
![](https://img.shields.io/static/v1?label=&message=macOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Windows&color=blue)
![](https://img.shields.io/static/v1?label=&message=iOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Android&color=blue)

A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.

This project provides a robust, event-driven logging and tracing platform specifically designed for Kotlin
Multiplatform (also compatible with the Java ecosystem). Built with coroutines and channels at its core, it offers
asynchronous, scalable logging across multiple platforms.

This project also tries to be fully compatible with `OpenTelemetry` standard.

📖 [Documentation](https://smyrgeorge.github.io/log4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## Usage

```kotlin
// https://central.sonatype.com/artifact/io.github.smyrgeorge/log4k-coroutines
implementation("io.github.smyrgeorge:log4k-coroutines:x.y.z")
```

## Examples

```kotlin
Logger.factory = SimpleCoroutinesLoggerFactory()
val trace: Tracer = Tracer.of(this::class)
val log = Logger.of(this::class)

log.info("Hello from coroutines logger!")

val parent: TracingEvent.Span.Remote = trace.span(id = "ID_EXAMPLE", traceId = "TRACE_ID_EXAMPLE")
val ctx = TracingContext.builder().with(parent).build()

withContext(ctx) {
    val ctx = TracingContext.current()
    log.info("Hello from coroutines logger with context=$ctx!")

    ctx.span("span-1") {
        log.info("Hello from span '${ctx.currentOrNull()?.name}'!")
        ctx.span("span-2") {
            log.info("Hello from span '${ctx.currentOrNull()?.name}'!")
            ctx.span("span-3") {
                log.info("Hello from span '${ctx.currentOrNull()?.name}'!")
            }
        }
    }

    log.info("Hello from coroutines logger with context=$ctx!")
}
```
