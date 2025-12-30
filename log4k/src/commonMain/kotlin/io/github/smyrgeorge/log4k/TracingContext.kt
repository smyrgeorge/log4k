package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.CoroutinesTracingContext
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

    /**
     * A builder class used to construct instances of `LoggingContext`.
     * It allows for the incremental configuration of a `LoggingContext`'s properties,
     * such as a parent span and a tracer.
     *
     * The main purpose of this class is to provide a fluent interface for customizing
     * and building a specific `LoggingContext` instance.
     */
    class Builder {
        private var tracer: Tracer? = null
        private var parent: Span? = null
        fun with(parent: Span): Builder = apply { this.parent = parent }
        fun with(tracer: Tracer): Builder = apply { this.tracer = tracer }
        fun build(): CoroutinesTracingContext = CoroutinesTracingContext(tracer, parent)
    }

    companion object {
        /**
         * Creates and returns a new instance of `Builder` to construct and configure a `LoggingContext`.
         *
         * The `Builder` provides a fluent interface for setting up the properties of `LoggingContext`,
         * such as the parent span and tracer, before building the final instance.
         *
         * @return a new instance of `Builder` for constructing a `LoggingContext`.
         */
        fun builder(): Builder = Builder()

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
                span.exception(e, true)
                span.end(e)
                throw e
            } finally {
                spans.pop()
            }
        }

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
