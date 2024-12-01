package io.github.smyrgeorge.log4k.coroutines

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.MutableTags
import io.github.smyrgeorge.log4k.impl.Tag
import io.github.smyrgeorge.log4k.impl.Tags
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Represents a logging context that is used within a coroutine for tracing and tagging operations.
 * It functions as a coroutine context element, managing tags and a stack of spans.
 * Provides constructors to initialize with different combinations of tags and a span.
 */
@Suppress("unused")
class LoggingContext : CoroutineContext.Element {
    val tags: Tags
    val spans: SpanStack = SpanStack()

    constructor() {
        tags = emptyMap()
    }

    constructor(tags: Tags) {
        this.tags = tags
    }

    constructor(span: Span) {
        tags = emptyMap()
        spans.push(span)
    }

    constructor(tags: Tags, span: Span) {
        this.tags = tags
        spans.push(span)
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
        val current = spans.peek() ?: error("No span found!")
        val span = current.context.tracer.span(name, current).start().also {
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
        val EMPTY: LoggingContext = of()
        fun builder(): Builder = Builder()
        fun of(): LoggingContext = LoggingContext()
        fun of(span: Span): LoggingContext = LoggingContext(span)
        fun of(span: Span, tags: Tags): LoggingContext = LoggingContext(tags, span)
        fun of(span: Span, vararg tags: Tag): LoggingContext = LoggingContext(tags.toMap(), span)

        /**
         * Retrieves the current `LoggingContext` from the coroutine's context.
         * If there is no `LoggingContext` present, it returns an empty context.
         *
         * @return The current `LoggingContext` if available, or an empty context if not.
         */
        suspend fun current(): LoggingContext = coroutineContext[LoggingContext] ?: EMPTY
    }

    /**
     * Builder for constructing a `LoggingContext` object with optional configurations.
     *
     * This class allows you to configure a `LoggingContext` by adding a `Span` or a collection of `Tags`.
     * The configurations are accumulated and applied in the final `build()` method, which produces
     * an instance of `LoggingContext`.
     */
    class Builder {
        private var span: Span? = null
        private var tags: MutableTags = mutableMapOf()
        fun with(span: Span): Builder = apply { this.span = span }
        fun with(f: (MutableTags) -> Unit): Builder = apply { f(tags) }
        fun with(vararg tags: Tag): Builder = apply { this.tags.putAll(tags) }
        fun with(tags: Tags): Builder = apply { this.tags.putAll(tags) }
        fun build(): LoggingContext = span?.let { LoggingContext(tags, it) } ?: LoggingContext(tags)
    }

    /**
     * A simple stack data structure to manage `Span` objects, allowing push, pop, and peek operations.
     * Provides functionalities to add a new span to the stack, remove the most recently added span,
     * or view it without removing.
     */
    class SpanStack() {
        private val stack = mutableListOf<Span>()
        fun push(span: Span) = stack.add(span)
        fun pop(): Span? = stack.removeLastOrNull()
        fun peek(): Span? = stack.lastOrNull()
        fun current(): Span? = peek()
        override fun toString(): String = stack.joinToString(separator = ", ", prefix = "[", postfix = "]") { it.name }
    }
}