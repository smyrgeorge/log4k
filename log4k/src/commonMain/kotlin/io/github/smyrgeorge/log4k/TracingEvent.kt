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
        private val mutex = Mutex()
        private var idx: Int = 0
        private var started: Boolean = false
        private var closed: Boolean = false
        private fun idx(): Int = ++idx

        fun start(): Span = withLock {
            started = true
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

        fun event(msg: String, vararg args: Any?) {
            // If not started, return
            if (!started) return
            // If already ended, return.
            if (closed) return
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
            val event = End(
                id = id,
                level = level,
                tracer = tracer.name,
            )
            RootLogger.trace(event)
        }

        private fun <T> withLock(f: () -> T): T = runBlocking { mutex.withLock { f() } }

        class Start(
            override var id: String,
            val name: String,
            override val level: Level,
            override val tracer: String,
            val parent: String?,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null,
        ) : TracingEvent

        class Event(
            override val id: String,
            val spanId: String,
            override val level: Level,
            override val tracer: String,
            val message: String,
            val arguments: Array<out Any?>,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null
        ) : TracingEvent

        class End(
            override var id: String,
            override val level: Level,
            override val tracer: String,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null,
        ) : TracingEvent
    }
}