# log4k-slf4j-appender

![Build](https://github.com/smyrgeorge/log4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/log4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/log4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/log4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/log4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

![](https://img.shields.io/static/v1?label=&message=Platforms&color=grey)
![](https://img.shields.io/static/v1?label=&message=Jvm&color=blue)

A **[log4k](../README.md) appender that forwards logging events to SLF4J** — the reverse direction of
[log4k-slf4j](../log4k-slf4j/README.md).

Use it to adopt log4k inside an existing JVM project that already has an SLF4J backend (Logback, Log4j2, …)
configured: everything emitted through the log4k API — including the entry/exit lines the
[compiler plugin](../README.md#compiler-plugin) generates for `@Logged` functions — lands in that backend instead of
log4k's default console appender. Your existing configuration (patterns, files, rolling policies, JSON encoders) keeps
working; log4k becomes just another source of events.

This module is **JVM-only**. The other log4k modules are multiplatform; SLF4J itself is a JVM API, so the bridge exists
only for the `jvm` target.

📖 [Documentation](https://smyrgeorge.github.io/log4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## Table of Contents

- [Installation](#installation)
- [How It Works](#how-it-works)
- [Level Mapping](#level-mapping)
- [What Survives the Bridge](#what-survives-the-bridge)
- [Do Not Combine With log4k-slf4j](#do-not-combine-with-log4k-slf4j)

## Installation

```kotlin
// https://central.sonatype.com/artifact/io.github.smyrgeorge/log4k-slf4j-appender
implementation("io.github.smyrgeorge:log4k-slf4j-appender:x.y.z")
```

The module transitively exposes both `log4k` and `slf4j-api` (2.x), so you do not need to declare them
yourself. Then, once at startup:

```kotlin
Slf4jLoggingAppender.install()
```

`install()` unregisters log4k's default console appender (so nothing is logged twice) and registers the
SLF4J appender in its place; appenders you registered deliberately are left untouched. If you prefer to
manage the registry yourself:

```kotlin
RootLogger.Logging.appenders.unregister(SimpleConsoleLoggingAppender::class)
RootLogger.Logging.appenders.register(Slf4jLoggingAppender())
```

## How It Works

`Slf4jLoggingAppender` is a regular log4k `Appender<LoggingEvent>`: it consumes events from `RootLogger`'s
asynchronous queue, resolves an `org.slf4j.Logger` with the event's logger name, and forwards the event
through SLF4J's fluent API.

The forwarding hands over the **raw** message pattern together with the untouched `arguments` array — never
a pre-formatted string. log4k's `{}` placeholder rules deliberately mirror SLF4J's `MessageFormatter`, so
substitution can be left to the backend, and structured encoders (JSON, logstash, …) keep the individual
arguments. A `Throwable` on the event becomes the SLF4J cause; a span-correlated event carries its
`traceId`/`spanId` as SLF4J key-value pairs.

Two level gates apply in this setup: log4k's own logger levels decide whether an event is published at all,
and the backend's configuration decides whether the forwarded call is written. Keep the log4k side at least
as verbose as the backend (levels disabled in the backend cost only a no-op builder), so the backend
configuration remains the single source of truth.

## Level Mapping

log4k's levels map one-to-one onto SLF4J's:

| log4k         | SLF4J   |
|---------------|---------|
| `Level.TRACE` | `TRACE` |
| `Level.DEBUG` | `DEBUG` |
| `Level.INFO`  | `INFO`  |
| `Level.WARN`  | `WARN`  |
| `Level.ERROR` | `ERROR` |
| `Level.OFF`   | —       |

`Level.OFF` has no SLF4J counterpart; events carrying it are dropped by the appender.

## What Survives the Bridge

Delivery goes through `RootLogger`'s asynchronous queue, so the backend observes the forwarding coroutine,
not the original call site:

- **Message, arguments, throwable, logger name, level** — forwarded verbatim.
- **Span correlation** — forwarded as `traceId`/`spanId` key-value pairs (rendered by `%kvp` in Logback, and
  kept as fields by structured encoders).
- **Thread name, timestamp** — the backend records its own; the original values remain available on the
  `LoggingEvent` (`thread`, `timestamp`) if you need a custom bridge with full fidelity.
- **Caller location** (`%class`, `%method`, `%line`) — not available; those patterns would describe the
  appender, not your code.

## Do Not Combine With log4k-slf4j

[log4k-slf4j](../log4k-slf4j/README.md) bridges the *opposite* direction: it makes log4k the backend for
SLF4J. With both on the classpath, every event forwarded to SLF4J would be routed straight back into log4k —
an endless loop. The two modules must never be combined; `Slf4jLoggingAppender` fails fast at construction
with a descriptive error if it detects that SLF4J is bound to the log4k provider.
