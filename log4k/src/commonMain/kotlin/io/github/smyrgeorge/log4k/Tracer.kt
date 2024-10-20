package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.reflect.KClass

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class Tracer(
    override val name: String,
    private var level: Level
) : LoggerRegistry.Collector {
    private var levelBeforeMute: Level = level

    override fun getLevel(): Level = level

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

    fun span(name: String): TracingEvent.Span =
        span(name, null as? TracingEvent.Span?)

    inline fun <T> span(name: String, parent: TracingEvent.Span? = null, f: (TracingEvent.Span) -> T): T {
        val span = span(name, parent).start()
        return try {
            f(span)
        } finally {
            span.end()
        }
    }

    fun span(id: String, name: String, parent: TracingEvent.Span?): TracingEvent.Span =
        TracingEvent.Span(id, name, level, parent, this)

    fun span(id: String, name: String): TracingEvent.Span =
        span(id, name, null as? TracingEvent.Span?)

    inline fun <T> span(id: String, name: String, parent: TracingEvent.Span? = null, f: (TracingEvent.Span) -> T): T {
        val span = span(id, name, parent).start()
        return try {
            f(span)
        } finally {
            span.end()
        }
    }

    companion object {
        fun of(name: String): Tracer = RootLogger.Tracing.factory.get(name)
        fun of(clazz: KClass<*>): Tracer = RootLogger.Tracing.factory.get(clazz)
    }
}