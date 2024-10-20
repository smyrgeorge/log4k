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
    val timestamp: Instant
    val tracer: String
    val thread: String?

    class Span(
        val id: String,
        val name: String,
        val level: Level,
        val parent: Span? = null,
        val tracer: Tracer,
    ) {
        private fun shouldLog(level: Level): Boolean =
            level.ordinal >= tracer.getLevel().ordinal

        private val mutex = Mutex()
        private var idx: Int = 0
        private var started: Boolean = false
        private var closed: Boolean = false
        private fun idx(): Int = ++idx

        fun start(): Span = withLock {
            started = true
            if (!shouldLog(tracer.getLevel())) return@withLock this
            val event = Start(
                id = id,
                name = name,
                level = level,
                tracer = tracer.name,
                parent = parent?.id,
            )
            RootLogger.trace(event)
            this
        }

        fun event(name: String, f: (MutableMap<String, Any?>) -> Unit): Unit =
            event(name, tracer.getLevel(), f)

        fun event(name: String, level: Level, f: (MutableMap<String, Any?>) -> Unit) {
            val attributes = mutableMapOf<String, Any?>()
            f(attributes)
            event(name, level, attributes)
        }

        fun event(name: String, attributes: Map<String, Any?> = emptyMap()): Unit =
            event(name, tracer.getLevel(), attributes)

        fun event(name: String, level: Level, attributes: Map<String, Any?> = emptyMap()): Unit = withLock {
            // If not started, return
            if (!started) return@withLock
            // If already ended, return.
            if (closed) return@withLock
            if (!shouldLog(level)) return@withLock
            val event = Event(
                id = "$id-${idx()}",
                name = name,
                spanId = id,
                attributes = attributes,
                level = level,
                tracer = tracer.name,
                timestamp = Clock.System.now()
            )
            RootLogger.trace(event)
        }

        fun end(error: Throwable? = null): Unit = withLock {
            // If not started, return
            if (!started) return@withLock
            // If already ended, return.
            if (closed) return@withLock
            closed = true
            if (!shouldLog(tracer.getLevel())) return@withLock
            val event = End(
                id = id,
                level = level,
                tracer = tracer.name,
                status = error?.let { Status.ERROR } ?: Status.OK,
                error = error,
            )
            RootLogger.trace(event)
        }

        private fun <T> withLock(f: () -> T): T = runBlocking { mutex.withLock { f() } }

        enum class Status { OK, ERROR }

        data class Start(
            override var id: String,
            val name: String,
            override val level: Level,
            override val tracer: String,
            val parent: String?,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null,
        ) : TracingEvent

        data class Event(
            override val id: String,
            val name: String,
            val spanId: String,
            val attributes: Map<String, Any?>,
            override val level: Level,
            override val tracer: String,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null
        ) : TracingEvent

        data class End(
            override var id: String,
            override val level: Level,
            override val tracer: String,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null,
            private var status: Status,
            private var error: Throwable?
        ) : TracingEvent
    }
}