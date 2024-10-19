package io.github.smyrgeorge.log4k

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
        parent: String? = null,
        val logger: Logger,
    ) {
        private val mutex = Mutex()
        private var idx: Int = 0
        private var closed: Boolean = false
        private fun idx(): Int = ++idx

        init {
            val event = Start(
                id = id,
                name = name,
                level = level,
                tracer = logger.name,
                parent = parent,
            )
            RootLogger.trace(event)
        }

        fun event(msg: String, vararg args: Any?): Unit = withLock {
            // If already ended, return.
            if (closed) return@withLock
            val event = Event(
                id = "$id-${idx()}",
                spanId = id,
                level = level,
                tracer = logger.name,
                message = msg,
                arguments = args,
                timestamp = Clock.System.now()
            )
            RootLogger.trace(event)
        }

        fun end(): Unit = withLock {
            // If already ended, return.
            if (closed) return@withLock
            closed = true

            val event = End(
                id = id,
                level = level,
                tracer = logger.name,
            )
            RootLogger.trace(event)
        }

        private fun withLock(f: () -> Unit) = runBlocking { mutex.withLock { f() } }

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

        class Start(
            override var id: String,
            val name: String,
            override val level: Level,
            override val tracer: String,
            val parent: String?,
            override val timestamp: Instant = Clock.System.now(),
            override val thread: String? = null,
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