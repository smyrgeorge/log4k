package io.github.smyrgeorge.log4k

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Suppress("MemberVisibilityCanBePrivate")
interface TracingEvent {
    val id: String
    val level: Level
    val tracer: Tracer

    // https://opentelemetry.io/docs/specs/otel/trace/api/#span
    @Suppress("unused")
    class Span(
        override val id: String,
        override val level: Level,
        override val tracer: Tracer,
        val name: String,
        val parent: Span? = null,
    ) : TracingEvent {
        var status: Status = Status.UNSET
        var start: Instant? = null
        var end: Instant? = null
        val events: MutableList<Event> = mutableListOf()
        var error: Throwable? = null

        private fun shouldLog(level: Level): Boolean =
            level.ordinal >= tracer.level.ordinal

        private val mutex = Mutex()
        private var idx: Int = 0
        private var started: Boolean = false
        private var closed: Boolean = false
        private fun idx(): Int = ++idx

        fun start(): Span = withLock {
            if (started || !shouldLog(tracer.level)) return@withLock this
            start = Clock.System.now()
            started = true
            this
        }

        fun event(name: String, level: Level, attrs: Map<String, Any?> = emptyMap()): Unit = withLock {
            if (!started || closed) return@withLock
            if (!shouldLog(level)) return@withLock

            val event = Event(
                id = "$id-${idx()}",
                name = name,
                attributes = attrs,
                level = level,
                tracer = tracer,
                timestamp = Clock.System.now()
            )
            events.add(event)
        }

        fun end(error: Throwable? = null): Unit = withLock {
            // If not started, return
            if (closed || !started) return@withLock
            end = Clock.System.now()
            closed = true
            this.error = error
            status = error?.let { Status.ERROR } ?: Status.OK
            if (!shouldLog(tracer.level)) return@withLock
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

        fun event(name: String, f: (MutableMap<String, Any?>) -> Unit) = event(name, tracer.level, f)
        fun event(name: String, attrs: Map<String, Any?> = emptyMap()) = event(name, tracer.level, attrs)

        fun event(name: String, level: Level, f: (MutableMap<String, Any?>) -> Unit) {
            mutableMapOf<String, Any?>().also {
                f(it)
                event(name, level, it)
            }
        }

        enum class Status { UNSET, OK, ERROR }

        private fun <T> withLock(f: () -> T): T = runBlocking { mutex.withLock { f() } }
        override fun toString(): String {
            return "Span(id='$id', level=$level, tracer=$tracer, name='$name', parent=$parent, status=$status, start=$start, end=$end, error=$error, events=$events)"
        }
    }

    // https://opentelemetry.io/docs/specs/otel/trace/api/#add-events
    data class Event(
        override val id: String,
        override val level: Level,
        override val tracer: Tracer,
        val name: String,
        val attributes: Map<String, Any?>,
        val timestamp: Instant,
    ) : TracingEvent
}