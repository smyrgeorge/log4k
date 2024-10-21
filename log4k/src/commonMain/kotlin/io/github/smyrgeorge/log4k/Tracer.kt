package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.reflect.KClass

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class Tracer(
    override val name: String,
    override var level: Level
) : LoggerRegistry.Collector {
    private lateinit var levelBeforeMute: Level

    override fun mute() {
        levelBeforeMute = level
        level = Level.OFF
    }

    override fun unmute() {
        level = levelBeforeMute
        levelBeforeMute = level
    }

    fun span(id: String, traceId: String, name: String): TracingEvent.Span =
        TracingEvent.Span.of(id = id, level = level, tracer = this, name = name, traceId = traceId, isRemote = true)

    fun span(name: String, parent: TracingEvent.Span? = null): TracingEvent.Span =
        TracingEvent.Span.of(id = RootLogger.Tracing.id(), level = level, tracer = this, name = name, parent = parent)

    inline fun <T> span(
        name: String,
        parent: TracingEvent.Span? = null,
        f: (TracingEvent.Span) -> T
    ): T {
        val span = span(name, parent).start()
        return try {
            f(span).also { span.end() }
        } catch (e: Throwable) {
            span.exception(e, true)
            span.end(e)
            throw e
        }
    }

    companion object {
        fun of(name: String): Tracer = RootLogger.Tracing.factory.get(name)
        fun of(clazz: KClass<*>): Tracer = RootLogger.Tracing.factory.get(clazz)
    }
}