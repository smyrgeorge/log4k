package io.github.smyrgeorge.log4k.coroutines

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.MutableTags
import io.github.smyrgeorge.log4k.impl.Tag
import io.github.smyrgeorge.log4k.impl.Tags
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Suppress("unused", "MemberVisibilityCanBePrivate")
class LoggingContext(
    val tags: Tags = emptyMap(),
) : CoroutineContext.Element {
    val spans: SpanStack = SpanStack()

    constructor(span: Span) : this() {
        spans.push(span)
    }

    constructor(tags: Tags, span: Span) : this(tags = tags) {
        spans.push(span)
    }

    inline fun <T> span(name: String, f: Span.Local.() -> T): T {
        val current = spans.peek() ?: error("No span found!")
        val span = current.context.tracer.span(name, current).also {
            it.start()
            spans.push(it)
        }
        return try {
            f(span).also {
                span.end()
                spans.pop()
            }
        } catch (e: Throwable) {
            span.exception(e, true)
            span.end(e)
            spans.pop()
            throw e
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

        suspend fun current(): LoggingContext = coroutineContext[LoggingContext] ?: EMPTY
    }

    class Builder {
        private var span: Span? = null
        private var tags: MutableTags = mutableMapOf()
        fun with(span: Span): Builder = apply { this.span = span }
        fun with(f: (MutableTags) -> Unit): Builder = apply { f(tags) }
        fun with(vararg tags: Tag): Builder = apply { this.tags.putAll(tags) }
        fun with(tags: Tags): Builder = apply { this.tags.putAll(tags) }
        fun build(): LoggingContext = span?.let { LoggingContext(tags, it) } ?: LoggingContext(tags)
    }

    class SpanStack() {
        private val stack = mutableListOf<Span>()
        fun push(span: Span) = stack.add(span)
        fun pop(): Span? = stack.removeLastOrNull()
        fun peek(): Span? = stack.lastOrNull()
        fun isEmpty(): Boolean = stack.isEmpty()
        fun isNotEmpty(): Boolean = stack.isNotEmpty()
        fun clear() = stack.clear()
        override fun toString(): String = stack.joinToString(separator = ", ", prefix = "[", postfix = "]") { it.name }
    }
}