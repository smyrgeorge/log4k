package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.SimpleCoroutinesTracingContext
import io.github.smyrgeorge.log4k.impl.Tags
import kotlinx.coroutines.currentCoroutineContext

interface TracingContext {
    val tracer: Tracer?
    val parent: Span?

    var current: Span?
    fun currentOrNull(): Span? = current
    fun current(): Span = currentOrNull() ?: error("No span found in current context.")

    companion object {
        /**
         * Creates a new instance of `SimpleCoroutinesTracingContext` with the specified tracer and parent span.
         *
         * @param tracer An optional `Tracer` instance used for managing tracing spans. Default is `null`.
         * @param parent An optional `Span` that indicates the parent trace context. Default is `null`.
         * @return A newly created `SimpleCoroutinesTracingContext` instance initialized with the provided tracer and parent span.
         */
        fun create(
            tracer: Tracer? = null,
            parent: Span? = null
        ): SimpleCoroutinesTracingContext = SimpleCoroutinesTracingContext(tracer, parent)

        /**
         * Creates a new instance of the `SimpleCoroutinesTracingContext.Builder` class.
         *
         * The builder provides a fluent interface for configuring and constructing
         * a `SimpleCoroutinesTracingContext` instance. It allows for setting properties
         * such as a parent span and a tracer before building the context.
         *
         * @return A new instance of `SimpleCoroutinesTracingContext.Builder` for building tracing contexts.
         */
        fun builder(): SimpleCoroutinesTracingContext.Builder = SimpleCoroutinesTracingContext.Builder()

        /**
         * Creates a new tracing span within the current tracing context, executing the given function
         * within the scope of the new span.
         *
         * @param name The name of the span to create.
         * @param f The lambda function to execute within the newly created span.
         * @return The result produced by the execution of the provided function within the span.
         */
        inline fun <T> TracingContext.span(name: String, f: Span.Local.() -> T): T = span(name, emptyMap(), f)

        /**
         * Creates a new span with the specified name and tags, executes the given block of code within the context of the span,
         * and automatically handles starting, ending, and exception management for the span.
         *
         * @param name The name of the span to be created.
         * @param tags A map of tags to be associated with the span. Defaults to an empty map.
         * @param f The block of code to be executed within the context of the created span.
         * @return The result of the block execution.
         */
        inline fun <T> TracingContext.span(name: String, tags: Tags = emptyMap(), f: Span.Local.() -> T): T =
            traced(context = this, parent = null, tracer = null, name = name, tags = tags, f = f)

        /**
         * The runtime helper the `log4k-compiler-plugin` generates a call to for
         * [io.github.smyrgeorge.log4k.annotation.Traced]. Executes [f] inside a new span whose parent
         * and tracer are resolved from whichever of [context], [parent] or [tracer] is in scope (the
         * plugin passes exactly one): the [context]'s current span, else [parent], else a new root
         * span from [tracer]. The span is started, ended, and — if [f] throws — marked failed (the
         * exception is recorded and rethrown).
         *
         * @param context a [TracingContext] in scope, if any; its current span becomes the parent and
         *   the new span becomes its current span for the duration of [f].
         * @param parent a [Span] in scope, if any, used as the parent when there is no [context].
         * @param tracer used to create a root span when neither [context] nor [parent] is present.
         * @param name the name of the span to create.
         * @param tags optional tags to attach to the span at creation.
         * @param f the block to execute within the new span.
         * @return the result produced by [f].
         */
        inline fun <T> traced(
            context: TracingContext?,
            parent: Span?,
            tracer: Tracer?,
            name: String,
            tags: Tags = emptyMap(),
            f: Span.Local.() -> T
        ): T {
            val parentSpan: Span? = context?.currentOrNull() ?: parent
            val effectiveTracer: Tracer = parentSpan?.context?.tracer ?: tracer ?: context?.tracer
                ?: error("No tracer found for span '$name'.")
            val span = effectiveTracer.span(name, tags, parentSpan).start()
            context?.current = span
            return try {
                f(span).also { span.end() }
            } catch (e: Throwable) {
                span.exception(e)
                span.end(e)
                throw e
            } finally {
                context?.current = parentSpan
            }
        }

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
