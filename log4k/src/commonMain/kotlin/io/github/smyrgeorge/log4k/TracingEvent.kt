package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.OpenTelemetry
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Suppress("MemberVisibilityCanBePrivate", "unused")
interface TracingEvent {
    // https://opentelemetry.io/docs/specs/otel/trace/api/#span
    data class Span(
        val name: String,
        val level: Level,
        val context: Context,
        val parent: Span?,
        var startAt: Instant?,
        var endAt: Instant?,
        val attributes: MutableMap<String, Any?>,
        val events: MutableList<Event>,
        var status: Status,
    ) : TracingEvent {

        private fun shouldStart(): Boolean =
            !context.isRemote && level.ordinal >= context.tracer.level.ordinal

        private fun shouldLogEvent(level: Level): Boolean =
            !context.isRemote && level.ordinal >= level.ordinal

        private var started: Boolean = false
        private var closed: Boolean = false

        fun start(): Span {
            if (!shouldStart()) return this
            if (started) return this
            startAt = Clock.System.now()
            started = true
            return this
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

        companion object {
            fun of(
                id: String,
                name: String,
                level: Level,
                tracer: Tracer,
                parent: Span? = null,
                isRemote: Boolean = false,
                traceId: String = parent?.context?.traceId ?: id,
            ) = Span(
                name = name,
                level = level,
                context = Context(traceId, id, isRemote, Context.Tracer(tracer.name, tracer.level)),
                parent = parent,
                startAt = null,
                endAt = null,
                attributes = mutableMapOf(),
                events = mutableListOf(),
                status = Status(),
            )
        }
    }
}