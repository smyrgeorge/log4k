package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracerFactory

class SimpleTracerFactory : TracerFactory() {
    override fun create(name: String): Tracer = SimpleTracer(name, RootLogger.level)
}