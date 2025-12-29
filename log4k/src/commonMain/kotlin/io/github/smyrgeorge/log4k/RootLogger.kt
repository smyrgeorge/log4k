package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.extensions.dispatcher
import io.github.smyrgeorge.log4k.impl.registry.AppenderRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The RootLogger object serves as the central logging and tracing utility for the application.
 * It facilitates logging, tracing, and metering by providing an integrated framework for handling these activities.
 *
 * Properties:
 * - level: Represents the logging level for the logger. Default is INFO.
 *
 * Initialization:
 * Upon initialization, the RootLogger registers a default logging appender and starts consuming both
 * the logging and tracing event queues.
 *
 * Nested Objects:
 * - Logging: Handles logging related operations including generating unique log IDs, registering log appenders, and accessing loggers.
 * - Tracing: Handles tracing related operations such as registering trace appenders and accessing tracers.
 * - Metering: Handles metering operations like registering meter appenders.
 *
 * Functions:
 * - log(event: LoggingEvent): Sends a logging event to the logging channel.
 * - trace(event: TracingEvent): Sends a tracing event to the tracing channel.
 * - meter(event: MeteringEvent): Sends a metering event to the metering channel.
 */
object RootLogger {
    private val dispatcher: CoroutineDispatcher = dispatcher()
    private val logs: Channel<LoggingEvent> = Channel(capacity = Channel.UNLIMITED)
    private val traces: Channel<TracingEvent> = Channel(capacity = Channel.UNLIMITED)
    private val meters: Channel<MeteringEvent> = Channel(capacity = Channel.UNLIMITED)

    init {
        Logging.appenders.register(SimpleConsoleLoggingAppender())

        // Start consuming the Logging queue.
        RootLoggerScope.launch(dispatcher) {
            logs.consumeEach { event ->
                runCatching {
                    event.id = Logging.id()
                    Logging.appenders.all().forEach { it.append(event) }
                }
            }
        }

        // Start consuming the Tracing queue.
        RootLoggerScope.launch(dispatcher) {
            traces.consumeEach { event ->
                runCatching {
                    Tracing.appenders.all().forEach { it.append(event) }
                }
            }
        }

        // Start consuming the Tracing queue.
        RootLoggerScope.launch(dispatcher) {
            meters.consumeEach { event ->
                runCatching {
                    event.id = Logging.id()
                    Metering.appenders.all().forEach { it.append(event) }
                }
            }
        }
    }

    /**
     * Logs a specified logging event.
     *
     * @param event The logging event to be recorded.
     * @return Unit This function does not return a value.
     */
    fun log(event: LoggingEvent): Unit = send(RootLoggerScope) { logs.send(event) }

    /**
     * Sends a tracing event through the tracer scope.
     *
     * @param event The tracing event to be sent.
     * @return Unit.
     */
    fun trace(event: TracingEvent): Unit = send(RootLoggerScope) { traces.send(event) }

    /**
     * Meters a given metering event and sends it using the provided metering infrastructure.
     *
     * @param event The metering event to be measured and sent.
     * @return Unit
     */
    fun meter(event: MeteringEvent): Unit = send(RootLoggerScope) { meters.send(event) }

    /**
     * Provides logging functionality with various configurable logging levels.
     *
     * This object manages the current logging level and provides unique identifiers
     * for logging events. It also maintains a registry of appenders that handle
     * log events.
     */
    object Logging {
        var level: Level = Level.INFO
        private var idx: Long = 0
        fun id(): Long = ++idx
        val appenders = AppenderRegistry<LoggingEvent>()
    }

    /**
     * Object responsible for managing tracing configurations and appenders.
     *
     * This object allows setting the logging level and prefix for tracing spans.
     * It also holds an instance of `AppenderRegistry` to manage tracing event appenders.
     * The `AppenderRegistry` is used for registering, retrieving, and managing
     * different appenders that handle `TracingEvent`.
     */
    object Tracing {
        var level: Level = Level.INFO
        val appenders = AppenderRegistry<TracingEvent>()
    }

    /**
     * Singleton object responsible for managing the metering system.
     * Provides functionality for generating unique identifiers for metering events,
     * maintaining the logging level, and registering appenders for handling
     * metering events.
     */
    object Metering {
        var level: Level = Level.INFO
        private var idx: Long = 0
        fun id(): Long = ++idx
        val appenders = AppenderRegistry<MeteringEvent>()
    }

    private inline fun send(scope: CoroutineScope, crossinline f: suspend () -> Unit) {
        scope.launch { f() }
    }

    private object RootLoggerScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}
