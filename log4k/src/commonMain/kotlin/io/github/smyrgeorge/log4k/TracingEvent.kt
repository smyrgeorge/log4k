package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.MutableTags
import io.github.smyrgeorge.log4k.impl.OpenTelemetryAttributes
import io.github.smyrgeorge.log4k.impl.Tags
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Sealed interface representing a tracing event in a system.
 */
sealed interface TracingEvent {
    /**
     * Represents a span in a tracing system, which can either be a [Local] or [Remote] span.
     *
     * A span is a unit of work within a trace and may contain child spans, tags, events, and a status.
     * https://opentelemetry.io/docs/specs/otel/trace/api/#span
     *
     * @property name The name of the span.
     * @property level The logging level of the span.
     * @property context The context of the span, providing trace and span identifiers.
     * @property parent The parent span, if any, can be null.
     * @property id The unique identifier of the span.
     * @property startAt The start timestamp of the span, set when the span starts.
     * @property endAt The end timestamp of the span, set when the span ends.
     * @property tags A map of tags associated with the span.
     * @property events A list of events associated with the span.
     * @property status The status of the span, containing the result of its execution.
     */
    abstract class Span(
        val id: String,
        val name: String,
        val level: Level,
        val context: Context,
        val parent: Span?,
        var startAt: Instant?,
        var endAt: Instant?,
        val tags: MutableTags,
        val events: MutableList<Event>,
        var status: Status,
    ) : TracingEvent {
        /**
         * Creates and returns a new local span with the given name associated with the current context.
         *
         * @param name The name of the new span.
         * @param tags Optional tags to associate with the span.
         * @return A new instance of `Local`.
         */
        fun span(name: String, tags: Tags = emptyMap()): Local =
            context.tracer.span(name, tags, this)

        /**
         * Executes a function within the scope of a tracing span.
         *
         * @param T The type of the result produced by the function.
         * @param name The name of the span.
         * @param tags Optional tags to associate with the span.
         * @param f A function to be executed within the local span context.
         * @return The result produced by the function `f`.
         */
        inline fun <T> span(name: String, tags: Tags = emptyMap(), f: Local.() -> T): T =
            context.tracer.span(name, tags, this, f)

        /**
         * Represents a local span in a tracing system. A span is a unit of work within a trace and can
         * contain child spans.
         *
         * @param id The unique identifier for the span.
         * @param name The name of the span.
         * @param level The level of the span.
         * @param tracer The tracer associated with this span.
         * @param parent The parent span, if any. Default is `null`.
         * @param tags Additional tags to associate with the span.
         * @param traceId The unique identifier for the trace. Defaults to the parent's traceId or the span's own id.
         */
        class Local(
            id: String,
            name: String,
            level: Level,
            tracer: Tracer,
            parent: Span? = null,
            tags: Tags = emptyMap(),
            traceId: String = parent?.context?.traceId ?: Tracer.traceId()
        ) : Span(
            id = id,
            name = name,
            level = level,
            context = Context(traceId, id, false, tracer),
            parent = parent,
            startAt = null,
            endAt = null,
            tags = tags.toMutableMap(),
            events = mutableListOf(),
            status = Status()
        ) {

            private fun shouldStart(): Boolean =
                !context.isRemote
                        && level != Level.OFF
                        && level.ordinal >= context.tracer.level.ordinal

            private fun shouldLogEvent(level: Level): Boolean =
                !context.isRemote
                        && level != Level.OFF
                        && level.ordinal >= this.level.ordinal

            private var started: Boolean = false
            private var closed: Boolean = false

            /** Whether the span is open (started and not yet ended) — the window in which events may be recorded. */
            @PublishedApi
            internal fun isOpen(): Boolean = started && !closed

            /**
             * Whether an event at [level] would be recorded right now: the span [isOpen] and
             * [level] passes the span's level gate. Published so the inline tag-populating
             * overloads can skip invoking their block entirely for a dropped event.
             */
            @PublishedApi
            internal fun shouldRecordEvent(level: Level): Boolean = isOpen() && shouldLogEvent(level)

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
             * The record/drop decision is made once, at [start]: a started span always ends and is
             * emitted, even if the tracer was muted or its level raised mid-flight — the span must
             * never be left half-open.
             *
             * @param error Optional throwable error to record. If provided, the status will
             *              be set to `Status.Code.ERROR`, otherwise it will be `Status.Code.OK`.
             */
            fun end(error: Throwable? = null) {
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

            /**
             * Records an event with the given name, level, and tags. Recorded only while the span
             * is open (started and not yet ended) and the event's level passes the span's level.
             *
             * @param name The name of the event.
             * @param level The logging level of the event, determining its severity.
             * @param tags A map of tags associated with the event, defaults to an empty map.
             */
            fun event(name: String, level: Level, tags: Tags = emptyMap()) {
                if (!shouldRecordEvent(level)) return
                val event = Event(
                    name = name,
                    tags = tags,
                    timestamp = Clock.System.now()
                )
                events.add(event)
            }

            /**
             * https://opentelemetry.io/docs/specs/otel/trace/exceptions/
             * Records an exception event with the given tags. Recorded only while the span is open
             * (started and not yet ended) — in particular, never after [end] has already emitted
             * the span to the appenders — but unlike [event] it is not subject to level filtering.
             *
             * @param error The throwable error to be recorded.
             * @param tags A map of additional tags to associate with the exception event.
             */
            fun exception(error: Throwable, tags: Tags = emptyMap()) {
                if (!isOpen()) return
                val event = Event(
                    name = OpenTelemetryAttributes.EXCEPTION,
                    timestamp = Clock.System.now(),
                    tags = tags + mapOf(
                        OpenTelemetryAttributes.EXCEPTION_TYPE to error::class.toName(),
                        OpenTelemetryAttributes.EXCEPTION_MESSAGE to (error.message ?: ""),
                        OpenTelemetryAttributes.EXCEPTION_STACKTRACE to error.stackTraceToString(),
                    )
                )
                events.add(event)
            }

            /**
             * Records an exception event with the given tags.
             *
             * [f] is invoked only when the exception will actually be recorded (the span is open),
             * so populating the tags costs nothing when the event would be dropped.
             *
             * @param error The throwable error to be recorded.
             * @param f Function to populate a mutable map of additional tags to associate with the exception event.
             */
            fun exception(error: Throwable, f: (MutableTags) -> Unit) {
                if (!isOpen()) return
                mutableMapOf<String, Any>().also {
                    f(it)
                    exception(error, it)
                }
            }

            inline fun trace(name: String, f: (MutableTags) -> Unit) = event(name, Level.TRACE, f)
            inline fun debug(name: String, f: (MutableTags) -> Unit) = event(name, Level.DEBUG, f)
            inline fun info(name: String, f: (MutableTags) -> Unit) = event(name, Level.INFO, f)
            inline fun warn(name: String, f: (MutableTags) -> Unit) = event(name, Level.WARN, f)
            inline fun error(name: String, f: (MutableTags) -> Unit) = event(name, Level.ERROR, f)

            fun trace(name: String, tags: Tags = emptyMap()) = event(name, Level.TRACE, tags)
            fun debug(name: String, tags: Tags = emptyMap()) = event(name, Level.DEBUG, tags)
            fun info(name: String, tags: Tags = emptyMap()) = event(name, Level.INFO, tags)
            fun warn(name: String, tags: Tags = emptyMap()) = event(name, Level.WARN, tags)
            fun error(name: String, tags: Tags = emptyMap()) = event(name, Level.ERROR, tags)

            fun event(name: String, tags: Tags = emptyMap()) = event(name, level, tags)
            inline fun event(name: String, f: (MutableTags) -> Unit) = event(name, level, f)

            /**
             * Records an event with tags populated by [f]. The block is invoked only when the event
             * will actually be recorded (the span is open and [level] passes its gate) — mirroring
             * [Logger.at], populating the tags costs nothing when the event is filtered out.
             */
            inline fun event(name: String, level: Level, f: (MutableTags) -> Unit) {
                if (!shouldRecordEvent(level)) return
                mutableMapOf<String, Any>().also {
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
            name: String = "remote-$id",
            level: Level,
            tracer: Tracer
        ) : Span(
            id = id,
            name = name,
            level = level,
            context = Context(traceId, id, true, tracer),
            parent = null,
            startAt = null,
            endAt = null,
            tags = mutableMapOf(),
            events = mutableListOf(),
            status = Status()
        ) {
            override fun toString(): String = "Remote${super.toString()}"
        }

        // https://opentelemetry.io/docs/specs/otel/trace/api/#spancontext
        data class Context(
            val traceId: String,
            val spanId: String,
            val isRemote: Boolean, // Indicates whether the Span was received from somewhere else or locally generated.
            val tracer: Tracer, // Information about the local [Tracer].
        ) {
            override fun toString(): String {
                return "Context(spanId='$spanId', traceId='$traceId')"
            }
        }

        // https://opentelemetry.io/docs/specs/otel/trace/api/#add-events
        data class Event(
            val name: String,
            val timestamp: Instant,
            val tags: Tags,
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
            return "Span(name='$name', level=$level, context=$context, parent=$parent, startAt=$startAt, endAt=$endAt, tags=$tags, events=$events, status=$status)"
        }
    }
}
