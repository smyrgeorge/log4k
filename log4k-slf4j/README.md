# log4k-slf4j

![Build](https://github.com/smyrgeorge/log4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/log4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/log4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/log4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/log4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

![](https://img.shields.io/static/v1?label=&message=Platforms&color=grey)
![](https://img.shields.io/static/v1?label=&message=Jvm&color=blue)

An **SLF4J 2.x provider backed by [log4k](../README.md)**.

Add it to a JVM project and every `org.slf4j.Logger` call — from your own code and from every third-party library that
logs through SLF4J (Spring, Netty, Hibernate, …) — is routed into log4k's asynchronous, channel-based pipeline and
handled by log4k appenders. There is nothing to configure: SLF4J discovers the provider on the classpath.

This module is **JVM-only**. The other log4k modules are multiplatform; SLF4J itself is a JVM API, so the bridge exists
only for the `jvm` target.

📖 [Documentation](https://smyrgeorge.github.io/log4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## Table of Contents

- [Installation](#installation)
- [How It Works](#how-it-works)
- [Level Mapping](#level-mapping)
- [Spring Boot](#spring-boot)

## Installation

```kotlin
// https://central.sonatype.com/artifact/io.github.smyrgeorge/log4k-slf4j
implementation("io.github.smyrgeorge:log4k-slf4j:x.y.z")
```

The module transitively exposes both `log4k` and `slf4j-api`, so you do not need to declare them yourself.

> [!IMPORTANT]
> This is an **SLF4J 2.x** provider. It is discovered through `ServiceLoader` via
> `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`. The legacy SLF4J 1.7 binding mechanism
> (`StaticLoggerBinder`) is **not** implemented — pin `slf4j-api` to `2.x` and make sure no other SLF4J provider
> (Logback, `slf4j-simple`, `log4j-slf4j2-impl`, …) is on the classpath, or SLF4J will pick one of them arbitrarily and
> warn about the ambiguity.

Verify the wiring at startup:

```kotlin
println(LoggerFactory.getILoggerFactory()) // io.github.smyrgeorge.log4k.slf4j.Log4kILoggerFactory@…
```

## How It Works

`Log4kSLF4JServiceProvider` is the `SLF4JServiceProvider` entry point, supplying a `Log4kILoggerFactory` alongside a
`BasicMarkerFactory` and `BasicMDCAdapter`. That factory resolves every SLF4J logger name through log4k's own
`Logger.factory` and returns a `Log4kLogger` — an `org.slf4j.Logger` implementing only `handleNormalizedLoggingCall`, so
SLF4J normalizes the arguments before they reach `Logger.log(…)`, which enqueues a `LoggingEvent` on `RootLogger`'s
channel for the appenders to consume.

Two consequences follow:

**SLF4J loggers *are* log4k loggers.** `LoggerFactory.getLogger("com.acme.Service")` and `Logger.of("com.acme.Service")`
resolve the same instance from the same registry, so anything the log4k API can do to a logger — change its level, mute
it — also applies to loggers created by third-party libraries.

**SLF4J calls are non-blocking.** Nothing is written on the calling thread: the event is enqueued and appended on
`Dispatchers.IO`, and `{}` substitution happens later, in the appender. Appenders therefore receive the raw message
pattern plus the untouched `arguments` array, which is what makes structured/JSON output possible.

## Level Mapping

SLF4J's levels map one-to-one onto log4k's, and `isDebugEnabled()` & friends delegate to `Logger.isEnabled(level)`:

| SLF4J   | log4k         |
|---------|---------------|
| `TRACE` | `Level.TRACE` |
| `DEBUG` | `Level.DEBUG` |
| `INFO`  | `Level.INFO`  |
| `WARN`  | `Level.WARN`  |
| `ERROR` | `Level.ERROR` |
| —       | `Level.OFF`   |

`Level.OFF` has no SLF4J counterpart; it is what muting a logger sets, and it makes every `isXxxEnabled()` return
`false`.

## Spring Boot

Spring Boot ships Logback via `spring-boot-starter-logging`. Leaving it in place puts two SLF4J providers on the
classpath, and SLF4J will warn and bind to whichever it finds first. Exclude it:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation("io.github.smyrgeorge:log4k-slf4j:x.y.z")
}
```

If the starter is pulled in transitively by several dependencies, exclude it globally instead:

```kotlin
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}
```
