package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggerFactory
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.registry.AppenderRegistry
import io.github.smyrgeorge.log4k.registry.LoggerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("MemberVisibilityCanBePrivate")
object RootLogger {
    private val events: Channel<LoggingEvent> =
        Channel(capacity = Channel.UNLIMITED)

    val level: Level = Level.INFO
    val factory = LoggerFactory()
    val loggers = LoggerRegistry()
    val appenders = AppenderRegistry()

    init {
        register(ConsoleAppender())

        // Start consuming the logging queue.
        LoggerScope.launch(Dispatchers.IO) {
            events.consumeEach { event ->
                event.id = nextIdx()
                appenders.all().forEach { it.append(event) }
            }
        }
    }

    fun log(event: LoggingEvent) = runBlocking { events.send(event) }
    fun register(appender: Appender) = appenders.register(appender)

    private object LoggerScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }

    private var idx: Long = 0
    private val idxMutex = Mutex()
    private suspend fun nextIdx(): Long = idxMutex.withLock { ++idx }
}