package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.OpenTelemetry
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Suppress("MemberVisibilityCanBePrivate", "unused")
interface TracingEvent {
    // https://opentelemetry.io/docs/specs/otel/trace/api/#span
    abstract class Span(
        open val name: String,
        open val level: Level,
        val context: Context,
        open val parent: Span?,
        var startAt: Instant?,
        var endAt: Instant?,
        val attributes: MutableMap<String, Any?>,
        val events: MutableList<Event>,
        var status: Status,
    ) : TracingEvent {

        /**
         * Constructor for creating a local Span.
         *
         * This constructor initializes a local Span with the provided identifier, name,
         * level, tracer, parent span, and trace identifier. The start and end timestamps
         * are initially set to null. Attributes and events are initialized to empty mutable
         * collections, and the status is initialized to a default Status object.
         *
         * @param id Unique identifier for the span.
         * @param name Name of the span.
         * @param level Logging level for the span.
         * @param tracer Tracer instance responsible for the span.
         * @param parent Parent span, if any; can be null.
         * @param traceId Identifier for the trace to which the span belongs.
         */
        constructor(id: String, name: String, level: Level, tracer: Tracer, parent: Span?, traceId: String) : this(
            name = name,
            level = level,
            context = Context(traceId, id, false, Context.Tracer(tracer.name, tracer.level)),
            parent = parent,
            startAt = null,
            endAt = null,
            attributes = mutableMapOf(),
            events = mutableListOf(),
            status = Status(),
        )

        /**
         * Constructor for creating a remote span instance with the given parameters.
         *
         * @param id The unique identifier of the span.
         * @param traceId The unique identifier of the trace.
         * @param name The name of the span.
         * @param level The logging level of the span.
         * @param tracer The tracer associated with this span.
         */
        constructor(id: String, traceId: String, name: String, level: Level, tracer: Tracer) : this(
            name = name,
            level = level,
            context = Context(traceId, id, true, Context.Tracer(tracer.name, tracer.level)),
            parent = null,
            startAt = null,
            endAt = null,
            attributes = mutableMapOf(),
            events = mutableListOf(),
            status = Status(),
        )

        /**
         * Represents a local span in a tracing system. A span is a unit of work within a trace and can
         * contain child spans.
         *
         * @param id The unique identifier for the span.
         * @param name The name of the span.
         * @param level The level of the span.
         * @param tracer The tracer associated with this span.
         * @param parent The parent span, if any. Default is `null`.
         * @param traceId The unique identifier for the trace. Defaults to the parent's traceId or the span's own id.
         */
        class Local(
            id: String,
            override val name: String,
            override val level: Level,
            tracer: Tracer,
            override val parent: Span? = null,
            traceId: String = parent?.context?.traceId ?: id
        ) : Span(id = id, name = name, level = level, tracer = tracer, parent = parent, traceId = traceId) {

            private fun shouldStart(): Boolean =
                !context.isRemote && level.ordinal >= context.tracer.level.ordinal

            private fun shouldLogEvent(level: Level): Boolean =
                !context.isRemote && level.ordinal >= level.ordinal

            private var started: Boolean = false
            private var closed: Boolean = false

            /**
             * Starts the local span if it hasn't been started already and if the
             * conditions to start the span are met.
             *
             * @return The current instance of `Local`.
             */
            fun start(): Local {
                if (!shouldStart()) return this
                if (started) return this
                startAt = Clock.System.now()
                started = true
                return this
            }

            /**
             * Ends the current span, recording an optional error and updating the status accordingly.
             * This method will do nothing if the span hasn't started or is already closed.
             *
             * @param error Optional throwable error to record. If provided, the status will
             *              be set to `Status.Code.ERROR`, otherwise it will be `Status.Code.OK`.
             */
            fun end(error: Throwable? = null) {
                if (!shouldStart()) return
                if (closed || !started) return
                endAt = Clock.System.now()
                closed = true
                status = Status(
                    code = error?.let { Status.Code.ERROR } ?: Status.Code.OK,
                    error = error,
                    description = error?.message,
                )
                RootLogger.trace(this)
            }

            fun event(name: String, level: Level, attrs: Map<String, Any?> = emptyMap()) {
                if (!shouldStart()) return
                if (!shouldLogEvent(level)) return
                if (!started || closed) return
                val event = Event(
                    name = name,
                    attributes = attrs,
                    timestamp = Clock.System.now()
                )
                events.add(event)
            }

            /**
             * https://opentelemetry.io/docs/specs/otel/trace/exceptions/
             * Records an exception event with the given attributes.
             *
             * @param error The throwable error to be recorded.
             * @param escaped A boolean indicating if the exception was propagated.
             * @param attrs A map of additional attributes to associate with the exception event.
             */
            fun exception(error: Throwable, escaped: Boolean, attrs: Map<String, Any?> = emptyMap()) {
                val event = Event(
                    name = OpenTelemetry.EXCEPTION,
                    timestamp = Clock.System.now(),
                    attributes = attrs + mapOf(
                        OpenTelemetry.EXCEPTION_TYPE to error::class.toName(),
                        OpenTelemetry.EXCEPTION_ESCAPED to escaped,
                        OpenTelemetry.EXCEPTION_MESSAGE to error.message,
                        OpenTelemetry.EXCEPTION_STACKTRACE to error.stackTraceToString(),
                    )
                )
                events.add(event)
            }

            fun exception(error: Throwable, escaped: Boolean, f: (MutableMap<String, Any?>) -> Unit) {
                val attributes: MutableMap<String, Any?> = mutableMapOf()
                f(attributes)
                exception(error, escaped, attributes)
            }

            inline fun trace(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.TRACE, f)
            inline fun debug(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.DEBUG, f)
            inline fun info(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.INFO, f)
            inline fun warn(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.WARN, f)
            inline fun error(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.ERROR, f)

            fun trace(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.TRACE, attrs)
            fun debug(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.DEBUG, attrs)
            fun info(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.INFO, attrs)
            fun warn(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.WARN, attrs)
            fun error(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.ERROR, attrs)

            fun event(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, level, f)
            fun event(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, level, attrs)

            inline fun event(name: String, level: Level, f: (MutableMap<String, Any?>) -> Unit) {
                mutableMapOf<String, Any?>().also {
                    f(it)
                    event(name, level, it)
                }
            }

            override fun toString(): String = "Local${super.toString()}"
        }

        /**
         * Represents a remote span in a distributed tracing system.
         *
         * @param id The unique identifier of the span.
         * @param traceId The unique identifier of the trace.
         * @param name The name of the span, defaulting to "remote-$id".
         * @param level The logging level of the span.
         * @param tracer The tracer associated with this span.
         */
        class Remote(
            id: String,
            traceId: String,
            override val name: String = "remote-$id",
            override val level: Level,
            tracer: Tracer
        ) : Span(id = id, traceId = traceId, name = name, level = level, tracer = tracer) {
            override fun toString(): String = "Remote${super.toString()}"
        }

        // https://opentelemetry.io/docs/specs/otel/trace/api/#spancontext
        data class Context(
            val traceId: String,
            val spanId: String,
            val isRemote: Boolean, // Indicates whether the Span was received from somewhere else or locally generated.
            val tracer: Tracer, // Information about the local [Tracer].
        ) {
            data class Tracer(val name: String, val level: Level)
        }

        // https://opentelemetry.io/docs/specs/otel/trace/api/#add-events
        data class Event(
            val name: String,
            val timestamp: Instant,
            val attributes: Map<String, Any?>,
        )

        // https://opentelemetry.io/docs/specs/otel/trace/api/#set-status
        data class Status(
            val code: Code = Code.UNSET,
            val error: Throwable? = null,
            val description: String? = null,
        ) {
            enum class Code { UNSET, OK, ERROR }
        }

        override fun toString(): String {
            return "Span(name='$name', level=$level, context=$context, parent=$parent, startAt=$startAt, endAt=$endAt, attributes=$attributes, events=$events, status=$status)"
        }
    }
}