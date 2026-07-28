package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingEvent.Span
import kotlinx.coroutines.currentCoroutineContext
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

/**
 * The `TracingContext` class represents an element in the coroutine context that holds tracing
 * and tagging information. It provides mechanisms for managing and tracking spans in a tracing
 * context and supports execution within the scope of a specific tracing span.
 *
 * The [current] slot is backed by an atomic reference, so it is safely published across the
 * threads a coroutine may hop between, and [restoreCurrent] is a real compare-and-set — a span
 * that finishes never clobbers a concurrent sibling's current span. Note that the slot itself is
 * still a single value: coroutines running *concurrently* in one context can observe a sibling as
 * their parent at creation time. For fully isolated parentage, give each concurrent branch its
 * own context (e.g. `TracingContext.create(tracer, parentSpan)` per `async` branch).
 *
 * @property tracer The tracer instance used to create and manage spans, if available.
 * @property parent The parent span of the current context, or `null` if no parent exists.
 * @property current The current span in the context, or `null` if no span is active.
 */
@OptIn(ExperimentalAtomicApi::class)
class SimpleCoroutinesTracingContext(
    override val tracer: Tracer? = null,
    override val parent: Span? = null,
) : TracingContext, CoroutineContext.Element {
    private val _current: AtomicReference<Span?> = AtomicReference(parent)

    override var current: Span?
        get() = _current.load()
        set(value) = _current.store(value)

    override fun restoreCurrent(expected: Span?, to: Span?) {
        _current.compareAndSet(expected, to)
    }

    override fun toString(): String {
        return "TracingContext(current=$current, parent=$parent)"
    }

    override val key: CoroutineContext.Key<SimpleCoroutinesTracingContext>
        get() = SimpleCoroutinesTracingContext

    /**
     * A builder class used to construct instances of `SimpleCoroutinesTracingContext`.
     * It allows for the incremental configuration of a `SimpleCoroutinesTracingContext`'s properties,
     * such as a parent span and a tracer.
     *
     * The main purpose of this class is to provide a fluent interface for customizing
     * and building a specific `SimpleCoroutinesTracingContext` instance.
     */
    class Builder {
        private var tracer: Tracer? = null
        private var parent: Span? = null
        fun with(parent: Span?): Builder = apply { this.parent = parent }
        fun with(tracer: Tracer?): Builder = apply { this.tracer = tracer }
        fun build(): SimpleCoroutinesTracingContext = SimpleCoroutinesTracingContext(tracer, parent)
    }

    companion object : CoroutineContext.Key<SimpleCoroutinesTracingContext> {
        /**
         * Retrieves the current tracing context from the coroutine context.
         *
         * @return The current [SimpleCoroutinesTracingContext] available in the coroutine context.
         * @throws IllegalStateException if no tracing context is found in the coroutine context.
         */
        suspend fun current(): SimpleCoroutinesTracingContext =
            currentCoroutineContext()[SimpleCoroutinesTracingContext] ?: error("No tracing context found.")
    }
}
