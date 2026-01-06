@file:Suppress("unused")

package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.SimpleTracerFactory
import io.github.smyrgeorge.log4k.impl.Tags
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * The `Tracer` class provides functionality for creating and managing tracing spans, both local and remote.
 * This abstract class implements the `CollectorRegistry.Collector` interface.
 *
 * @property name The name of the tracer.
 * @property level The logging level of the tracer.
 */
abstract class Tracer(
    final override val name: String,
    final override var level: Level
) : CollectorRegistry.Collector {
    override var levelBeforeMute: Level = level

    /**
     * Creates and returns a new local span with the given name and optional parent span.
     *
     * @param name The name of the new span.
     * @param parent The parent span, if any. Default is `null`.
     * @return A new instance of `TracingEvent.Span.Local`.
     */
    fun span(
        name: String,
        parent: TracingEvent.Span? = null
    ): TracingEvent.Span.Local = span(name, emptyMap(), parent)

    /**
     * Creates and returns a new local span with the given name, tags, and optional parent span.
     *
     * @param name The name of the new span.
     * @param tags The key-value pairs associated with the span.
     * @param parent The parent span, if any. Default is `null`.
     * @return A new instance of `TracingEvent.Span.Local`.
     */
    fun span(
        name: String,
        tags: Tags = emptyMap(),
        parent: TracingEvent.Span? = null
    ): TracingEvent.Span.Local =
        TracingEvent.Span.Local(id = spanId(), name = name, level = level, tracer = this, parent = parent, tags = tags)

    /**
     * Creates and returns a new remote span with the given id, trace ID, and an optional name.
     *
     * @param id the unique identifier for the span.
     * @param traceId the unique identifier for the trace.
     * @param name the name of the span, defaulting to "remote-$id" if not provided.
     * @return a new instance of `TracingEvent.Span.Remote`.
     */
    fun span(id: String, traceId: String, name: String = "remote-$id"): TracingEvent.Span.Remote =
        TracingEvent.Span.Remote(id = id, traceId = traceId, name = name, level = level, tracer = this)

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
    ): T = span(name, emptyMap(), parent, f)

    /**
     * Executes a function within the scope of a tracing span.
     *
     * @param T The type of the result produced by the function.
     * @param name The name of the span.
     * @param tags Optional tags to associate with the span.
     * @param parent The parent span, if any. Default is `null`.
     * @param f A function to be executed within the span context.
     * @return The result produced by the function `f`.
     */
    inline fun <T> span(
        name: String,
        tags: Tags = emptyMap(),
        parent: TracingEvent.Span? = null,
        f: TracingEvent.Span.Local.() -> T
    ): T {
        val span = span(name, tags, parent).start()
        return try {
            f(span).also { span.end() }
        } catch (e: Throwable) {
            span.exception(e)
            span.end(e)
            throw e
        }
    }

    override fun toString(): String {
        return "Tracer(name='$name', level=$level)"
    }

    companion object {
        /**
         * Generates a 16-character hexadecimal string representing a unique span identifier.
         *
         * @return A 16-character hexadecimal string pad-started with zeros if necessary.
         */
        fun spanId(): String = Random.nextLong().toULong().toString(16).padStart(16, '0')

        /**
         * Generates a trace identifier by concatenating two unique span identifiers.
         *
         * This method combines two 16-character hexadecimal strings to produce
         * a 32-character trace ID, ensuring uniqueness across spans within a trace context.
         *
         * @return A 32-character hexadecimal string representing the trace ID.
         */
        fun traceId(): String = buildString {
            append(spanId())
            append(spanId())
        }

        val registry = CollectorRegistry<Tracer>()
        var factory: TracerFactory = SimpleTracerFactory()
        fun of(name: String): Tracer = factory.get(name)
        fun of(clazz: KClass<*>): Tracer = factory.get(clazz)
    }
}
