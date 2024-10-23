package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.SimpleTracerFactory
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.extensions.dispatcher
import io.github.smyrgeorge.log4k.impl.registry.AppenderRegistry
import io.github.smyrgeorge.log4k.impl.registry.LoggerRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.absoluteValue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("MemberVisibilityCanBePrivate")
object RootLogger {
    val level: Level = Level.INFO
    private val dispatcher: CoroutineDispatcher = dispatcher()
    private val logs: Channel<LoggingEvent> = Channel(capacity = Channel.UNLIMITED)
    private val traces: Channel<TracingEvent> = Channel(capacity = Channel.UNLIMITED)

    init {
        Logging.register(SimpleConsoleLoggingAppender())

        // Start consuming the Logging queue.
        LoggerScope.launch(dispatcher) {
            logs.consumeEach { event ->
                event.id = Logging.id()
                Logging.appenders.all().forEach { it.append(event) }
            }
        }

        // Start consuming the Tracing queue.
        TracerScope.launch(dispatcher) {
            traces.consumeEach { event ->
                Tracing.appenders.all().forEach { it.append(event) }
            }
        }
    }

    fun log(event: LoggingEvent): Unit = send(LoggerScope) { logs.send(event) }
    fun trace(event: TracingEvent): Unit = send(TracerScope) { traces.send(event) }

    object Logging {
        private var idx: Long = 0
        fun id(): Long = ++idx
        val factory = SimpleLoggerFactory()
        val loggers = LoggerRegistry<Logger>()
        val appenders = AppenderRegistry<LoggingEvent>()
        fun register(appender: Appender<LoggingEvent>) = appenders.register(appender)
    }

    object Tracing {
        @OptIn(ExperimentalUuidApi::class)
        fun id(): String = "$prefix-${Uuid.random().hashCode().absoluteValue}"
        var prefix: String = "span"
        val factory = SimpleTracerFactory()
        val tracers = LoggerRegistry<Tracer>()
        val appenders = AppenderRegistry<TracingEvent>()
        fun register(appender: Appender<TracingEvent>) = appenders.register(appender)
    }

    private inline fun send(scope: CoroutineScope, crossinline f: suspend () -> Unit) {
        scope.launch { f() }
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