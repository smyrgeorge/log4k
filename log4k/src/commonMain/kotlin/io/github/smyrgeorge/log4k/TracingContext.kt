package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.SimpleCoroutinesTracingContext
import io.github.smyrgeorge.log4k.impl.Tags
import kotlinx.coroutines.currentCoroutineContext

interface TracingContext {
    val tracer: Tracer?
    val parent: Span?
    val spans: SpanStack

    fun currentOrNull(): Span? = spans.current()
    fun current(): Span = currentOrNull() ?: error("No span found in current context.")

    /**
     * A simple stack data structure to manage `Span` objects, allowing push, pop, and peek operations.
     * Provides functionalities to add a new span to the stack, remove the most recently added span,
     * or view it without removing.
     */
    class SpanStack {
        private val stack = mutableListOf<Span>()
        fun push(span: Span) = stack.add(span)
        fun pop(): Span? = stack.removeLastOrNull()
        fun peek(): Span? = stack.lastOrNull()
        fun current(): Span? = peek()
        override fun toString(): String = stack.joinToString(separator = ", ", prefix = "[", postfix = "]") { it.name }
    }

    companion object {
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
        inline fun <T> TracingContext.span(name: String, tags: Tags = emptyMap(), f: Span.Local.() -> T): T {
            val parent: Span? = spans.peek()
            val tracer = parent?.context?.tracer ?: tracer ?: error("No tracer found for current span.")
            val span = tracer.span(name, tags, parent).start().also {
                spans.push(it)
            }
            return try {
                f(span).also {
                    span.end()
                }
            } catch (e: Throwable) {
                span.exception(e)
                span.end(e)
                throw e
            } finally {
                spans.pop()
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
