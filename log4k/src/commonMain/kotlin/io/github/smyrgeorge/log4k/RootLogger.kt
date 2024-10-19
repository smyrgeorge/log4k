package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.appenders.SimpleConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.extensions.forEachParallel
import io.github.smyrgeorge.log4k.impl.registry.AppenderRegistry
import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("MemberVisibilityCanBePrivate")
object RootLogger {
    val level: Level = Level.INFO

    private val logs: Channel<LoggingEvent> =
        Channel(capacity = Channel.UNLIMITED)

    private val traces: Channel<TracingEvent> =
        Channel(capacity = Channel.UNLIMITED)

    init {
        Logging.register(SimpleConsoleLoggingAppender())

        // Start consuming the Logging queue.
        LoggerScope.launch(Dispatchers.IO) {
            logs.consumeEach { event ->
                event.id = Logging.id()
                Logging.appenders.all().forEachParallel { it.append(event) }
            }
        }

        // Start consuming the Tracing queue.
        TracerScope.launch(Dispatchers.IO) {
            traces.consumeEach { event ->
                Tracing.appenders.all().forEachParallel { it.append(event) }
            }
        }
    }

    fun log(event: LoggingEvent) = runBlocking { logs.send(event) }
    fun trace(event: TracingEvent) = runBlocking { traces.send(event) }

    object Logging {
        private var idx: Long = 0
        fun id(): Long = ++idx
        val factory = SimpleLoggerFactory()
        val loggers = LoggerRegistry()
        val appenders = AppenderRegistry<LoggingEvent>()
        fun register(appender: Appender<LoggingEvent>) = appenders.register(appender)
    }

    object Tracing {
        private var idx: Long = 0
        var prefix: String = "span"
        fun id(): String = runBlocking { "$prefix-${Clock.System.now().epochSeconds}-${idx()}" }
        private val mutex = Mutex()
        private suspend fun idx(): Long = mutex.withLock { ++idx }
        val appenders = AppenderRegistry<TracingEvent>()
        fun register(appender: Appender<TracingEvent>) = appenders.register(appender)
    }

    private object LoggerScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }

    private object TracerScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}