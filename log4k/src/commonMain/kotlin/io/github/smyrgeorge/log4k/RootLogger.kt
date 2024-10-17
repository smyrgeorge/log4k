package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.appenders.ConsoleAppender
import io.github.smyrgeorge.log4k.registry.AppenderRegistry
import io.github.smyrgeorge.log4k.registry.LoggerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("MemberVisibilityCanBePrivate")
object RootLogger {
    private val events: Channel<LoggingEvent> =
        Channel(capacity = Channel.UNLIMITED)

    val level: Level = Level.INFO
    val factory = SimpleLoggerFactory()
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

    private var idx: Long = 0
    private fun nextIdx(): Long = ++idx

    private object LoggerScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}