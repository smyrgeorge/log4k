package io.github.smyrgeorge.log4k

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Suppress("MemberVisibilityCanBePrivate", "unused")
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
        private fun shouldLog(): Boolean =
            level.ordinal >= tracer.getLevel().ordinal

        private val mutex = Mutex()
        private var idx: Int = 0
        private var started: Boolean = false
        private var closed: Boolean = false
        private fun idx(): Int = ++idx

        fun start(): Span = withLock {
            started = true
            if (!shouldLog()) return@withLock this
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

        fun event(msg: String, vararg args: Any?): Unit = withLock {
            // If not started, return
            if (!started) return@withLock
            // If already ended, return.
            if (closed) return@withLock
            if (!shouldLog()) return@withLock
            val event = Event(
                id = "$id-${idx()}",
                spanId = id,
                level = level,
                tracer = tracer.name,
                message = msg,
                arguments = args,
                timestamp = Clock.System.now()
            )
            RootLogger.trace(event)
        }

        fun end(): Unit = withLock {
            // If not started, return
            if (!started) return@withLock
            // If already ended, return.
            if (closed) return@withLock
            closed = true
            if (!shouldLog()) return@withLock
            val event = End(
                id = id,
                level = level,
                tracer = tracer.name,
            )
            RootLogger.trace(event)
        }

        private fun <T> withLock(f: () -> T): T = runBlocking { mutex.withLock { f() } }

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
            val spanId: String,
            override val level: Level,
            override val tracer: String,
            val message: String,
            val arguments: Array<out Any?>,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null
        ) : TracingEvent {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as Event
                if (id != other.id) return false
                if (spanId != other.spanId) return false
                if (level != other.level) return false
                if (tracer != other.tracer) return false
                if (message != other.message) return false
                if (timestamp != other.timestamp) return false
                if (thread != other.thread) return false
                return true
            }

            override fun hashCode(): Int {
                var result = id.hashCode()
                result = 31 * result + spanId.hashCode()
                result = 31 * result + level.hashCode()
                result = 31 * result + tracer.hashCode()
                result = 31 * result + message.hashCode()
                result = 31 * result + timestamp.hashCode()
                result = 31 * result + (thread?.hashCode() ?: 0)
                return result
            }
        }

        data class End(
            override var id: String,
            override val level: Level,
            override val tracer: String,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null,
        ) : TracingEvent
    }
}