package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingEvent.Span
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/**
 * The `LoggingContext` class represents an element in the coroutine context that holds tracing
 * and tagging information. It provides mechanisms for managing and tracking spans in a tracing
 * context and supports execution within the scope of a specific tracing span.
 *
 * @property tracer The tracer instance used to create and manage spans, if available.
 * @property parent The parent span of the current context, or `null` if no parent exists.
 * @property spans A stack structure to manage the active spans in this context.
 */
data class CoroutinesTracingContext(
    override val tracer: Tracer? = null,
    override val parent: Span? = null,
) : TracingContext, CoroutineContext.Element {
    override val spans: TracingContext.SpanStack = TracingContext.SpanStack()

    init {
        parent?.let { spans.push(it) }
    }

    override fun toString(): String {
        return "LoggingContext(spans=$spans)"
    }

    override val key: CoroutineContext.Key<CoroutinesTracingContext>
        get() = CoroutinesTracingContext

    companion object : CoroutineContext.Key<CoroutinesTracingContext> {
        fun builder(): TracingContext.Builder = TracingContext.Builder()

        /**
         * Retrieves the current tracing context from the coroutine context.
         *
         * @return The current [CoroutinesTracingContext] available in the coroutine context.
         * @throws IllegalStateException if no tracing context is found in the coroutine context.
         */
        suspend fun current(): CoroutinesTracingContext =
            currentCoroutineContext()[CoroutinesTracingContext] ?: error("No tracing context found.")
    }
}
