# log4k-integrations

![Build](https://github.com/smyrgeorge/log4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/log4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/log4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/log4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/log4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

![](https://img.shields.io/static/v1?label=&message=Platforms&color=grey)
![](https://img.shields.io/static/v1?label=&message=Jvm&color=blue)
![](https://img.shields.io/static/v1?label=&message=Linux&color=blue)
![](https://img.shields.io/static/v1?label=&message=macOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Windows&color=blue)
![](https://img.shields.io/static/v1?label=&message=iOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Android&color=blue)
![](https://img.shields.io/static/v1?label=&message=Js&color=blue)
![](https://img.shields.io/static/v1?label=&message=wasmJs&color=blue)

**[log4k](../README.md) appenders that integrate with third-party observability services.**

The module currently ships two tracing appenders, with room for more provider integrations over time:

| Appender                 | Protocol                                                                         | Works with                                                                                                      |
|--------------------------|----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `OtlpTracingAppender`    | [OTLP/HTTP](https://opentelemetry.io/docs/specs/otlp/) (`POST /v1/traces`, JSON) | OpenTelemetry Collector, Jaeger, Grafana Tempo, Honeycomb, Datadog Agent (OTLP ingest), and most other backends |
| `DatadogTracingAppender` | Datadog Agent trace intake (`PUT /v0.4/traces`, JSON)                            | Datadog Agent, and agent-compatible receivers (e.g. the OTel Collector's `datadog` receiver)                    |

Both build on log4k's `BatchAppender`: finished spans are accumulated and shipped in batches — a batch is
sent as soon as it reaches `batchSize` spans **or** when `batchTimeout` elapses since the first span of the
batch arrived, so low-traffic services still see their traces promptly.

The module is **multiplatform** and uses the [Ktor HTTP client](https://ktor.io/docs/client-engines.html).
It supports every log4k target except `wasmWasi` (which Ktor does not support).

📖 [Documentation](https://smyrgeorge.github.io/log4k/)

## Table of Contents

- [Installation](#installation)
- [OTLP (`OtlpTracingAppender`)](#otlp-otlptracingappender)
    - [Span Mapping (OTLP)](#span-mapping-otlp)
- [Datadog (`DatadogTracingAppender`)](#datadog-datadogtracingappender)
    - [Span Mapping (Datadog)](#span-mapping-datadog)

## Installation

```kotlin
// https://central.sonatype.com/artifact/io.github.smyrgeorge/log4k-integrations
implementation("io.github.smyrgeorge:log4k-integrations:x.y.z")
```

The module depends only on `ktor-client-core`; add the
[Ktor engine](https://ktor.io/docs/client-engines.html) matching your platform, e.g.:

```kotlin
implementation("io.ktor:ktor-client-cio:3.x.x")     // JVM (also: java, okhttp, apache)
implementation("io.ktor:ktor-client-darwin:3.x.x")  // iOS / macOS
implementation("io.ktor:ktor-client-curl:3.x.x")    // Linux / Windows
implementation("io.ktor:ktor-client-js:3.x.x")      // JS / wasmJs
```

With exactly one engine on the classpath it is discovered automatically; alternatively pass one explicitly
via the `engine` constructor parameter.

## OTLP (`OtlpTracingAppender`)

The vendor-neutral choice: [OTLP/HTTP](https://opentelemetry.io/docs/specs/otlp/) is accepted natively by
nearly every modern tracing backend. The default endpoint is `http://localhost:4318` (the standard
`/v1/traces` path is appended).

```kotlin
RootLogger.Tracing.appenders.register(
    OtlpTracingAppender(
        service = "my-service",
        // endpoint = "http://localhost:4318",   // the default
        // batchSize = 100, batchTimeout = 2.seconds,
        env = "production",                      // -> deployment.environment.name
        version = "1.4.2",                       // -> service.version
        // Most vendors authenticate with a header:
        // headers = mapOf("x-honeycomb-team" to "..."),
    )
)
```

### Span Mapping (OTLP)

log4k's span model is already OpenTelemetry-shaped, so the mapping is direct:

| log4k                        | OTLP                                                     |
|------------------------------|----------------------------------------------------------|
| `context.traceId` / `spanId` | `traceId` / `spanId` (hex, unchanged)                    |
| `parent.context.spanId`      | `parentSpanId`                                           |
| `name`                       | `name` (`kind` is `SPAN_KIND_INTERNAL`)                  |
| `startAt` / `endAt`          | `startTimeUnixNano` / `endTimeUnixNano`                  |
| tags                         | typed `attributes` (`bool`/`int`/`double`/`stringValue`) |
| `events`                     | span `events` (with typed attributes)                    |
| `status`                     | `status.code` / `status.message`                         |

When a span ends with an error but no explicit `exception` event was recorded (`end(error)` without
`span.exception(error)`), an `exception` event (`exception.type/message/stacktrace`) is synthesized from
the status, per the OpenTelemetry exception conventions — the stack trace is never lost. A 16-character
(64-bit) hex trace id is zero-padded; a non-hexadecimal or all-zero id (e.g. from a remote, propagated
context) is hashed to a stable valid one.

## Datadog (`DatadogTracingAppender`)

Talks to the Datadog Agent's APM trace intake API — the same endpoint the official Datadog tracers use.
The default agent URL is `http://localhost:8126`.

```kotlin
RootLogger.Tracing.appenders.register(
    DatadogTracingAppender(
        service = "my-service",
        // agentUrl = "http://localhost:8126",   // the default
        // batchSize = 100, batchTimeout = 2.seconds,
        env = "production",
        version = "1.4.2",
    )
)
```

Run the agent next to your service with APM enabled, e.g.:

```yaml
# docker-compose.yml
datadog-agent:
  image: gcr.io/datadoghq/agent:latest
  environment:
    DD_API_KEY: ${DD_API_KEY}
    DD_APM_ENABLED: "true"
    DD_APM_RECEIVER_PORT: "8126"
    DD_APM_NON_LOCAL_TRAFFIC: "true"
  ports:
    - "8126:8126"
```

Anything that speaks the agent protocol works the same way — including agent-compatible receivers such as
the [OpenTelemetry Collector's
`datadog` receiver](https://www.controltheory.com/resources/integrating-with-the-datadog-agent/),
which listens on the same port `8126` by default. Point `agentUrl` at whichever endpoint you deploy.
(The Datadog Agent also accepts OTLP — if you prefer the vendor-neutral protocol, use
`OtlpTracingAppender` against the agent's OTLP port instead.)

### Span Mapping (Datadog)

| log4k                           | Datadog                                          |
|---------------------------------|--------------------------------------------------|
| `context.traceId` (low 64 bits) | `trace_id` (high 64 bits kept in `_dd.p.tid`)    |
| `context.spanId`                | `span_id`                                        |
| `parent.context.spanId`         | `parent_id`                                      |
| `name`                          | `name` and `resource`                            |
| `startAt` / `endAt`             | `start` / `duration` (nanoseconds)               |
| `status.code == ERROR`          | `error = 1` plus `error.type/message/stack` meta |
| numeric tags                    | `metrics`                                        |
| all other tags                  | `meta` (stringified)                             |
| `events`                        | `meta.events` (JSON array, OTel-style)           |

The constructor's `service`, `env`, `version` and `spanType` apply to every span. log4k's
OpenTelemetry-style hex ids convert directly to Datadog's numeric ids; a non-hex id is hashed to a stable
64-bit id instead. Within a batch, spans are grouped by trace id, as the intake API expects
(`X-Datadog-Trace-Count`). Sampling is left to the agent (`_sampling_priority_v1 = 1`, AUTO_KEEP).
