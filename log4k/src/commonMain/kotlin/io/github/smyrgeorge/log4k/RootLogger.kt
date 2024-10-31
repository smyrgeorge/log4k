package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.SimpleMeterFactory
import io.github.smyrgeorge.log4k.impl.SimpleTracerFactory
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.extensions.dispatcher
import io.github.smyrgeorge.log4k.impl.registry.AppenderRegistry
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
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
    val level: Level = Level.INFO
    private val dispatcher: CoroutineDispatcher = dispatcher()
    private val logs: Channel<LoggingEvent> = Channel(capacity = Channel.UNLIMITED)
    private val traces: Channel<TracingEvent> = Channel(capacity = Channel.UNLIMITED)
    private val meters: Channel<MeteringEvent> = Channel(capacity = Channel.UNLIMITED)

    init {
        Logging.register(SimpleConsoleLoggingAppender())

        // Start consuming the Logging queue.
        LoggerScope.launch(dispatcher) {
            logs.consumeEach { event ->
                runCatching {
                    event.id = Logging.id()
                    Logging.appenders.all().forEach { it.append(event) }
                }
            }
        }

        // Start consuming the Tracing queue.
        TracerScope.launch(dispatcher) {
            traces.consumeEach { event ->
                runCatching {
                    Tracing.appenders.all().forEach { it.append(event) }
                }
            }
        }

        // Start consuming the Tracing queue.
        MeterScope.launch(dispatcher) {
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
    fun log(event: LoggingEvent): Unit = send(LoggerScope) { logs.send(event) }

    /**
     * Sends a tracing event through the tracer scope.
     *
     * @param event The tracing event to be sent.
     * @return Unit.
     */
    fun trace(event: TracingEvent): Unit = send(TracerScope) { traces.send(event) }

    /**
     * Meters a given metering event and sends it using the provided metering infrastructure.
     *
     * @param event The metering event to be measured and sent.
     * @return Unit
     */
    fun meter(event: MeteringEvent): Unit = send(MeterScope) { meters.send(event) }

    /**
     * Singleton object responsible for managing logging system.
     *
     * Provides unique identifiers for log entries, and manages registries for loggers
     * and appenders. Facilitates the registration of new appenders to process logging events.
     */
    object Logging {
        private var idx: Long = 0
        fun id(): Long = ++idx
        var factory: LoggerFactory = SimpleLoggerFactory()
        val loggers = CollectorRegistry<Logger>()
        val appenders = AppenderRegistry<LoggingEvent>()
        fun register(appender: Appender<LoggingEvent>) = appenders.register(appender)
    }

    /**
     * The `Tracing` object provides a centralized registry for tracers and appenders, enabling the management
     * of tracing spans across different parts of the application.
     *
     * @property prefix The prefix used for span identifiers.
     * @property factory An instance of `SimpleTracerFactory` used to create new tracers.
     * @property tracers A registry that maintains all registered tracers.
     * @property appenders A registry that maintains all registered appenders for tracing events.
     */
    object Tracing {
        var prefix: String = "span"
        var factory: TracerFactory = SimpleTracerFactory()
        val tracers = CollectorRegistry<Tracer>()
        val appenders = AppenderRegistry<TracingEvent>()
        fun register(appender: Appender<TracingEvent>) = appenders.register(appender)
    }

    /**
     * Singleton object responsible for metering operations, including creating and managing meters
     * and registering appenders for metering events.
     *
     * @property factory Defines the factory used to create meters. Defaults to `SimpleMeterFactory`.
     * @property meters Registry holding all the created and registered meters.
     * @property appenders Registry holding all the registered appenders for metering events.
     */
    object Metering {
        private var idx: Long = 0
        fun id(): Long = ++idx
        var factory: MeterFactory = SimpleMeterFactory()
        val meters = CollectorRegistry<Meter>()
        val appenders = AppenderRegistry<MeteringEvent>()
        fun register(appender: Appender<MeteringEvent>) = appenders.register(appender)
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

    private object MeterScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}