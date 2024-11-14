package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlin.math.absoluteValue
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The `Tracer` class provides functionality for creating and managing tracing spans, both local and remote.
 * This abstract class implements the `CollectorRegistry.Collector` interface.
 *
 * @property name The name of the tracer.
 * @property level The logging level of the tracer.
 */
@Suppress("unused")
abstract class Tracer(
    final override val name: String,
    final override var level: Level
) : CollectorRegistry.Collector {
    override var levelBeforeMute: Level = level

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

    /**
     * Executes a function within the scope of a tracing span.
     *
     * @param T The type of the result produced by the function.
     * @param name The name of the span.
     * @param parent The parent span, if any. Default is `null`.
     * @param f A function to be executed within the span context.
     * @return The result produced by the function `f`.
     */
    inline fun <T> span(
        name: String,
        parent: TracingEvent.Span? = null,
        f: TracingEvent.Span.Local.() -> T
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