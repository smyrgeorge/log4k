package io.github.smyrgeorge.log4k.slf4j.appender.utils

import kotlinx.coroutines.channels.Channel
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Marker
import org.slf4j.event.LoggingEvent
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.NOP_FallbackServiceProvider.REQUESTED_API_VERSION
import org.slf4j.spi.LoggingEventAware
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.event.Level as Slf4jLevel

/**
 * A capturing SLF4J backend, playing the role Logback would play in a real application. It is bound as
 * the test JVM's SLF4J provider via `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`, so a captured
 * call proves the full journey: log4k `Logger.log` (a level gate included) -> `RootLogger` queue ->
 * `Slf4jLoggingAppender` -> `LoggerFactory` -> backend logger.
 */
class CapturingSlf4jServiceProvider : SLF4JServiceProvider {
    private val markerFactory: IMarkerFactory = BasicMarkerFactory()
    private val mdcAdapter: MDCAdapter = BasicMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = CapturingSlf4jLoggerFactory
    override fun getMarkerFactory(): IMarkerFactory = markerFactory
    override fun getMDCAdapter(): MDCAdapter = mdcAdapter
    override fun getRequestedApiVersion(): String = REQUESTED_API_VERSION

    override fun initialize() {
        // NO-OP
    }
}

/**
 * An object (rather than an instance held by the provider), so tests can reach the very loggers the
 * appender resolves through `LoggerFactory` — e.g., to configure a backend-side level.
 */
object CapturingSlf4jLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, CapturingSlf4jLogger>()

    override fun getLogger(name: String): CapturingSlf4jLogger =
        loggers.computeIfAbsent(name) { CapturingSlf4jLogger(it) }
}

/**
 * A backend logger that records every call as a [Captured] instead of writing it anywhere.
 *
 * It implements [LoggingEventAware], as Logback's logger does, so the fluent-API path used by the
 * appender delivers the un-flattened event: raw message pattern, argument array, key-value pairs, and
 * throwable all survive to the assertion. The [level] property mimics a backend level configuration and
 * drives every `isXxxEnabled` answer, which is what SLF4J's `atLevel(...)` consults before handing out a
 * real (vs. NOP) builder.
 */
class CapturingSlf4jLogger(private val loggerName: String) : LegacyAbstractLogger(), LoggingEventAware {
    /** Backend-side threshold; calls below it are dropped by SLF4J before they reach this logger. */
    @Volatile
    var level: Slf4jLevel = Slf4jLevel.TRACE

    private fun enabled(requested: Slf4jLevel): Boolean = requested.toInt() >= level.toInt()

    override fun getName(): String = loggerName
    override fun isTraceEnabled(): Boolean = enabled(Slf4jLevel.TRACE)
    override fun isDebugEnabled(): Boolean = enabled(Slf4jLevel.DEBUG)
    override fun isInfoEnabled(): Boolean = enabled(Slf4jLevel.INFO)
    override fun isWarnEnabled(): Boolean = enabled(Slf4jLevel.WARN)
    override fun isErrorEnabled(): Boolean = enabled(Slf4jLevel.ERROR)
    override fun getFullyQualifiedCallerName(): String? = null

    /** Fluent-API entry point: `DefaultLoggingEventBuilder` targets [LoggingEventAware] loggers. */
    override fun log(event: LoggingEvent) {
        Slf4jCapture.record(
            Captured(
                logger = loggerName,
                level = event.level,
                message = event.message,
                arguments = event.argumentArray?.toList() ?: emptyList(),
                keyValues = (event.keyValuePairs ?: emptyList()).map { it.key to it.value },
                throwable = event.throwable,
            )
        )
    }

    /** Classic-API entry point; captured as well, so no call can slip through unobserved. */
    override fun handleNormalizedLoggingCall(
        level: Slf4jLevel,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        Slf4jCapture.record(
            Captured(
                logger = loggerName,
                level = level,
                message = messagePattern,
                arguments = arguments?.toList() ?: emptyList(),
                keyValues = emptyList(),
                throwable = throwable,
            )
        )
    }
}

/** A single call as observed by the capturing backend. */
data class Captured(
    val logger: String,
    val level: Slf4jLevel,
    val message: String?,
    val arguments: List<Any?>,
    val keyValues: List<Pair<String, Any?>>,
    val throwable: Throwable?,
)

/**
 * The sink every [CapturingSlf4jLogger] records into. Delivery is asynchronous (the appender runs on
 * `RootLogger`'s background consumer), so tests suspend on [await] until the call in question arrives.
 * The awaits filter by predicate and drain non-matching calls, so a stray log from elsewhere cannot make
 * an assertion pass or fail by accident.
 */
object Slf4jCapture {
    // UNLIMITED so `record` (called from the RootLogger consumer coroutine) never blocks; the channel
    // also provides the happens-before that publishes each capture to the awaiting test.
    private val delivered = Channel<Captured>(Channel.UNLIMITED)

    fun record(captured: Captured) {
        delivered.trySend(captured).getOrThrow()
    }

    /** Suspends until a capture matching [predicate] is recorded, draining any that do not match. */
    suspend fun await(predicate: (Captured) -> Boolean = { true }): Captured {
        while (true) {
            val captured = delivered.receive()
            if (predicate(captured)) return captured
        }
    }
}
