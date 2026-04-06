package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingEvent.Span

data object EmptyTracingContext : TracingContext {
    override val tracer: Tracer? = null
    override val parent: Span? = null
    override var current: Span? = null
}
