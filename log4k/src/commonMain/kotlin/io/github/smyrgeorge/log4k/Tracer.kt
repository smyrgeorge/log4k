package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlin.math.absoluteValue
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class Tracer(
    final override val name: String,
    final override var level: Level
) : LoggerRegistry.Collector {
    private var levelBeforeMute: Level = level

    override fun mute() {
        levelBeforeMute = level
        level = Level.OFF
    }

    override fun unmute() {
        level = levelBeforeMute
        levelBeforeMute = level
    }

    private fun id(): String {
        @OptIn(ExperimentalUuidApi::class)
        val hash = Uuid.random().hashCode()
        val id = if (hash < 0) "N${hash.absoluteValue}" else hash.toString()
        return "${RootLogger.Tracing.prefix}-$id"
    }

    fun span(id: String, traceId: String, name: String): TracingEvent.Span =
        TracingEvent.Span.of(id = id, level = level, tracer = this, name = name, traceId = traceId, isRemote = true)

    fun span(name: String, parent: TracingEvent.Span? = null): TracingEvent.Span =
        TracingEvent.Span.of(id = id(), level = level, tracer = this, name = name, parent = parent)

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
        inline fun <reified T : Tracer> of(name: String): T = of(name) as T
        inline fun <reified T : Tracer> of(clazz: KClass<*>): T = of(clazz) as T
    }
}