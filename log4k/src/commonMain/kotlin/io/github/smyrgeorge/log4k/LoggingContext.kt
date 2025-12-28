package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.MutableTags
import io.github.smyrgeorge.log4k.impl.Tag
import io.github.smyrgeorge.log4k.impl.Tags
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/**
 * The `LoggingContext` class represents an element in the coroutine context that holds tracing
 * and tagging information. It provides mechanisms for managing and tracking spans in a tracing
 * context and supports execution within the scope of a specific tracing span.
 *
 * @property tags A mutable map of tags associated with the context for tracing purposes.
 * @property tracer The tracer instance used to create and manage spans, if available.
 * @property parent The parent span of the current context, or `null` if no parent exists.
 * @property spans A stack structure to manage the active spans in this context.
 */
data class LoggingContext(
    val tags: MutableTags = mutableMapOf(),
    val tracer: Tracer? = null,
    val parent: Span? = null,
) : CoroutineContext.Element {
    val spans: SpanStack = SpanStack()

    init {
        parent?.let { spans.push(it) }
    }


    /**
     * Executes a function within the scope of a tracing span.
     *
     * @param T The type of the result produced by the function.
     * @param name The name of the span.
     * @param f A function to be executed within the span context.
     * @return The result produced by the function `f`.
     */
    inline fun <T> span(name: String, f: Span.Local.() -> T): T {
        val current: Span? = spans.peek()
        val tracer = current?.context?.tracer ?: tracer ?: error("No tracer found for current span.")
        val span = tracer.span(name, current).start().also {
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

    override fun toString(): String {
        return "LoggingContext(spans=$spans, tags=$tags)"
    }

    override val key: CoroutineContext.Key<LoggingContext>
        get() = LoggingContext

    companion object : CoroutineContext.Key<LoggingContext> {
        fun builder(): Builder = Builder()

        /**
         * Retrieves the current logging context from the coroutine context.
         *
         * @return The current [LoggingContext] available in the coroutine context.
         * @throws IllegalStateException if no logging context is found in the coroutine context.
         */
        suspend fun current(): LoggingContext =
            currentCoroutineContext()[LoggingContext] ?: error("No logging context found.")
    }

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
     * such as tags, a parent span, and a tracer.
     *
     * The main purpose of this class is to provide a fluent interface for customizing
     * and building a specific `LoggingContext` instance.
     */
    class Builder {
        private var tags: MutableTags = mutableMapOf()
        private var tracer: Tracer? = null
        private var parent: Span? = null
        fun with(parent: Span): Builder = apply { this.parent = parent }
        fun with(f: (MutableTags) -> Unit): Builder = apply { f(tags) }
        fun with(vararg tags: Tag): Builder = apply { this.tags.putAll(tags) }
        fun with(tags: Tags): Builder = apply { this.tags.putAll(tags) }
        fun with(tracer: Tracer): Builder = apply { this.tracer = tracer }
        fun build(): LoggingContext =
            parent?.let {
                LoggingContext(tags = tags, tracer = tracer, parent = it)
            } ?: LoggingContext(tags = tags, tracer = tracer)
    }
}