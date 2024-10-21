package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.withLockBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Suppress("MemberVisibilityCanBePrivate")
interface TracingEvent {
    val id: String
    val name: String

    // https://opentelemetry.io/docs/specs/otel/trace/api/#span
    @Suppress("unused")
    class Span(
        override val id: String,
        override val name: String,
        val level: Level,
        val tracer: Tracer,
        val parent: Span? = null,
        val traceId: String = parent?.traceId ?: id,
    ) : TracingEvent {
        var status: Status = Status.UNSET
        var start: Instant? = null
        var end: Instant? = null
        val events: MutableList<Event> = mutableListOf()
        var error: Throwable? = null

        private fun shouldStart(): Boolean =
            level.ordinal >= tracer.level.ordinal

        private fun shouldLogEvent(level: Level): Boolean =
            level.ordinal >= level.ordinal

        private val mutex = Mutex()
        private var idx: Int = 0
        private var started: Boolean = false
        private var closed: Boolean = false
        private fun idx(): Int = ++idx

        fun start(): Span = mutex.withLockBlocking {
            if (!shouldStart()) return@withLockBlocking this
            if (started) return@withLockBlocking this
            start = Clock.System.now()
            started = true
            this
        }

        fun event(name: String, level: Level, attrs: Map<String, Any?> = emptyMap()): Unit = mutex.withLockBlocking {
            if (!shouldStart()) return@withLockBlocking
            if (!shouldLogEvent(level)) return@withLockBlocking
            if (!started || closed) return@withLockBlocking
            val event = Event(
                id = "$id-${idx()}",
                name = name,
                attributes = attrs,
                timestamp = Clock.System.now()
            )
            events.add(event)
        }

        fun end(error: Throwable? = null): Unit = mutex.withLockBlocking {
            if (!shouldStart()) return@withLockBlocking
            if (closed || !started) return@withLockBlocking
            end = Clock.System.now()
            closed = true
            this.error = error
            status = error?.let { Status.ERROR } ?: Status.OK
            RootLogger.trace(this)
        }

        fun trace(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.TRACE, f)
        fun debug(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.DEBUG, f)
        fun info(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.INFO, f)
        fun warn(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.WARN, f)
        fun error(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, Level.ERROR, f)

        fun trace(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.TRACE, attrs)
        fun debug(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.DEBUG, attrs)
        fun info(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.INFO, attrs)
        fun warn(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.WARN, attrs)
        fun error(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, Level.ERROR, attrs)

        fun event(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, level, f)
        fun event(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, level, attrs)

        fun event(name: String, level: Level, f: (MutableMap<String, Any?>) -> Unit) {
            mutableMapOf<String, Any?>().also {
                f(it)
                event(name, level, it)
            }
        }

        enum class Status { UNSET, OK, ERROR }

        override fun toString(): String {
            return "Span(id='$id', traceId='$traceId', name='$name', parent=$parent, status=$status, start=$start, end=$end, error=${error?.message}, events=$events)"
        }
    }

    // https://opentelemetry.io/docs/specs/otel/trace/api/#add-events
    class Event(
        override val id: String,
        override val name: String,
        val attributes: Map<String, Any?>,
        val timestamp: Instant,
    ) : TracingEvent {
        override fun toString(): String {
            return "Event(id='$id', name='$name', attributes=$attributes, timestamp=$timestamp)"
        }
    }
}