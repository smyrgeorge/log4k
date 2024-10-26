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

    /**
     * Creates and returns a new local span with the given name and optional parent span.
     *
     * @param name The name of the new span.
     * @param parent The parent span, if any. Default is `null`.
     * @return A new instance of `TracingEvent.Span.Local`.
     */
    fun span(name: String, parent: TracingEvent.Span? = null): TracingEvent.Span.Local =
        TracingEvent.Span.Local(id = id(), level = level, tracer = this, name = name, parent = parent)

    /**
     * Creates and returns a new remote span with the given id, trace ID, and an optional name.
     *
     * @param id the unique identifier for the span.
     * @param traceId the unique identifier for the trace.
     * @param name the name of the span, defaulting to "remote-$id" if not provided.
     * @return a new instance of `TracingEvent.Span.Remote`.
     */
    fun span(id: String, traceId: String, name: String = "remote-$id"): TracingEvent.Span.Remote =
        TracingEvent.Span.Remote(id = id, level = level, tracer = this, name = name, traceId = traceId)

    inline fun <T> span(
        name: String,
        parent: TracingEvent.Span? = null,
        f: (TracingEvent.Span.Local) -> T
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
        inline fun <reified T : Tracer> ofType(name: String): T = of(name) as T
        inline fun <reified T : Tracer> ofType(clazz: KClass<*>): T = of(clazz) as T
    }
}