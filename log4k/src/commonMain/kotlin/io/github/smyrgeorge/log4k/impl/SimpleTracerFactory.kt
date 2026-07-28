package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracerFactory

/**
 * The default [TracerFactory]: creates a [SimpleTracer] whose level is a **snapshot** of
 * [RootLogger.Tracing.level] taken at creation time. Because instances are cached by name,
 * changing [RootLogger.Tracing.level] afterwards affects only tracers created from then on —
 * adjust existing ones through [Tracer.registry] (e.g. `Tracer.registry.setLevel(name, level)`).
 */
class SimpleTracerFactory : TracerFactory() {
    override fun create(name: String): Tracer = SimpleTracer(name, RootLogger.Tracing.level)
}
