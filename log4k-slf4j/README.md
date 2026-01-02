# log4k-slf4j

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
// https://central.sonatype.com/artifact/io.github.smyrgeorge/log4k-slf4j
implementation("io.github.smyrgeorge:log4k-slf4j:x.y.z")
```

### Spring Boot

If you add the SLF4J dependency to a Spring Boot project without adjustments, SLF4J will detect multiple providers and
raise an error. This happens because SLF4J’s auto-discovery mechanism finds more than one logging implementation (in
this case, both `log4k` and `logback`). To resolve this and ensure everything works smoothly, you need to exclude the
`logback` implementation from your Gradle dependencies.

Here’s how you can do it:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
        // Alternatively, exclude Logback specifically
        // exclude(group = "ch.qos.logback")
    }
}
``` 
