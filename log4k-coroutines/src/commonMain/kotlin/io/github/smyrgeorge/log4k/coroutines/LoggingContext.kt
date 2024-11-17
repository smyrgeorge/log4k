package io.github.smyrgeorge.log4k.coroutines

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.MutableTags
import io.github.smyrgeorge.log4k.impl.Tag
import io.github.smyrgeorge.log4k.impl.Tags
import kotlin.coroutines.CoroutineContext

@Suppress("unused", "MemberVisibilityCanBePrivate")
data class LoggingContext(
    val span: Span?,
    val tags: Tags
) : CoroutineContext.Element {

    class Builder {
        private var span: Span? = null
        private var tags: MutableTags = mutableMapOf()
        fun with(span: Span): Builder = apply { this.span = span }
        fun with(f: (MutableTags) -> Unit): Builder = apply { f(tags) }
        fun with(vararg tags: Tag): Builder = apply { this.tags.putAll(tags) }
        fun with(tags: Tags): Builder = apply { this.tags.putAll(tags) }
        fun build(): LoggingContext = LoggingContext(span, tags)
    }

    companion object : CoroutineContext.Key<LoggingContext> {
        val EMPTY: LoggingContext = of()
        fun builder(): Builder = Builder()
        fun of(): LoggingContext = LoggingContext(null, emptyMap())
        fun of(span: Span): LoggingContext = LoggingContext(span, emptyMap())
        fun of(span: Span, tags: Tags): LoggingContext = LoggingContext(span, tags)
        fun of(span: Span, vararg tags: Tag): LoggingContext =
            LoggingContext(span, tags.toMap())
    }

    override val key: CoroutineContext.Key<LoggingContext>
        get() = LoggingContext
}