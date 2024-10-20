package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.reflect.KClass

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class Tracer(
    override val name: String,
    private var level: Level
) : LoggerRegistry.Collector {
    private var levelBeforeMute: Level = level

    private fun Level.shouldLog(): Boolean =
        ordinal >= level.ordinal

    override fun setLevel(level: Level) {
        this.level = level
    }

    override fun mute() {
        levelBeforeMute = level
        level = Level.OFF
    }

    override fun unmute() {
        level = levelBeforeMute
        levelBeforeMute = level
    }

    fun span(name: String, parent: TracingEvent.Span?): TracingEvent.Span =
        TracingEvent.Span(RootLogger.Tracing.id(), name, level, parent, this)

    fun span(name: String, parent: String?): TracingEvent.Span {
        @Suppress("NAME_SHADOWING")
        val parent = parent?.let { spanOf(it, name) }
        return span(name, parent)
    }

    fun span(name: String): TracingEvent.Span =
        span(name, null as? TracingEvent.Span?)

    inline fun <T> span(name: String, parent: TracingEvent.Span? = null, f: (TracingEvent.Span) -> T): T {
        val span = span(name, parent)
        return try {
            f(span)
        } finally {
            span.end()
        }
    }

    fun span(id: String, name: String, parent: TracingEvent.Span? = null): TracingEvent.Span =
        TracingEvent.Span(id, name, level, parent, this)

    fun span(id: String, name: String, parent: String? = null): TracingEvent.Span {
        @Suppress("NAME_SHADOWING")
        val parent = parent?.let { spanOf(it, name) }
        return span(id, name, parent)
    }

    fun span(id: String, name: String): TracingEvent.Span =
        span(id, name, null as? TracingEvent.Span?)

    inline fun <T> span(id: String, name: String, parent: TracingEvent.Span? = null, f: (TracingEvent.Span) -> T): T {
        val span = span(id, name, parent)
        return try {
            f(span)
        } finally {
            span.end()
        }
    }

    private fun spanOf(spanId: String, name: String): TracingEvent.Span =
        TracingEvent.Span(spanId, name, level, null, this)

    companion object {
        fun of(name: String): Tracer = RootLogger.Tracing.factory.get(name)
        fun of(clazz: KClass<*>): Tracer = RootLogger.Tracing.factory.get(clazz)
    }
}