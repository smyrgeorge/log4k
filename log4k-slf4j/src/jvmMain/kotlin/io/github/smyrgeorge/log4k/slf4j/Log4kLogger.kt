package io.github.smyrgeorge.log4k.slf4j

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.SourceLocation
import io.github.smyrgeorge.log4k.impl.OpenTelemetryAttributes
import org.slf4j.MDC
import org.slf4j.Marker
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.spi.LoggingEventAware
import org.slf4j.event.LoggingEvent as Slf4jLoggingEvent

/**
 * A logger implementation that adapts the Log4k logging facade to a legacy abstract logger.
 *
 * Every classic `org.slf4j.Logger` method is inherited from [LegacyAbstractLogger]/`AbstractLogger` and
 * funnels into [handleNormalizedLoggingCall]. Deliberately *not* overriding the per-level methods is what
 * gives us SLF4J's argument normalization for free — most importantly, extracting a trailing [Throwable]
 * argument (`log.error("failed for {}", id, e)`) so it is attached to the event instead of being consumed
 * as a formatting argument. The base class also short-circuits on `isXxxEnabled()` before normalizing, so
 * disabled levels cost nothing.
 *
 * Fluent-API calls (`logger.atInfo().addKeyValue("tenant", "acme").log("…")`) arrive through
 * [LoggingEventAware.log] instead: SLF4J routes the fully built event here because this logger is
 * [LoggingEventAware], so the key-value pairs survive as the log4k event's **tags** — rather than being
 * flattened into the message text by SLF4J's non-aware fallback.
 *
 * A [SourceLocation] cannot be captured here at compile time — the SLF4J API transports no source location,
 * and the `log4k-compiler-plugin` instruments log4k entry points only (walking the stack at runtime on
 * every call is deliberately avoided). Instead, the bridge **recovers** one when the caller (or an
 * upstream instrumentation) has recorded it under a known convention:
 * - the OpenTelemetry code attributes — `code.file.path` / `code.line.number` / `code.function.name`
 *   (the legacy spellings `code.filepath` / `code.lineno` / `code.function` + `code.namespace` are
 *   accepted as well);
 * - or logstash-logback-encoder's caller-data fields — `caller_file_name` / `caller_line_number` /
 *   `caller_method_name` (+ `caller_class_name`), the shape Logback-based JSON logs use.
 *
 * The fluent event's **key-value pairs** are consulted first; pairs that formed the call site are
 * consumed into it instead of being duplicated as tags. Otherwise the **MDC** is read — on the
 * caller's thread, before the event enters log4k's asynchronous pipeline, so the propagated context
 * is the caller's. The file key is required (without it no [SourceLocation] is built); the line and
 * function are best-effort (`0` / `""` when absent).
 *
 * @property log4k The underlying Log4k `Logger` instance used for logging.
 */
public class Log4kLogger(
    private val log4k: Logger
) : LegacyAbstractLogger(), LoggingEventAware {
    override fun getName(): String = log4k.name

    override fun isTraceEnabled(): Boolean = log4k.isEnabled(Level.TRACE)
    override fun isDebugEnabled(): Boolean = log4k.isEnabled(Level.DEBUG)
    override fun isInfoEnabled(): Boolean = log4k.isEnabled(Level.INFO)
    override fun isWarnEnabled(): Boolean = log4k.isEnabled(Level.WARN)
    override fun isErrorEnabled(): Boolean = log4k.isEnabled(Level.ERROR)

    override fun getFullyQualifiedCallerName(): String? = null

    /** The classic (non-fluent) SLF4J path — no key-value pairs exist here, so the MDC is the only call-site source. */
    override fun handleNormalizedLoggingCall(
        level: org.slf4j.event.Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        log4k.log(
            level = level.toLevel(),
            span = null,
            tags = emptyMap(),
            message = messagePattern ?: "null",
            arguments = arguments ?: emptyArray(),
            throwable = throwable,
            callSite = callSiteFromMdc(),
        )
    }

    /**
     * The fluent-API path: forwards the built event's message, arguments, and throwable, and turns its
     * key-value pairs into the log4k event's tags. A `null` key-value value is rendered as `"null"`,
     * since log4k tag values are non-null. Key-value pairs recorded under the OpenTelemetry code
     * conventions become the event's [SourceLocation] (falling back to the MDC) and are consumed rather than
     * kept as tags.
     *
     * SLF4J hands the fluent event over verbatim, so the trailing-[Throwable] convention
     * (`log.atError().log("failed for {}", id, e)`) must be normalized *here*: when no cause was set via
     * `setCause`, a trailing [Throwable] argument is promoted to the event's throwable and trimmed from
     * the arguments — exactly what every other delivery route does with a fluent event (Logback's
     * event-aware logger, and SLF4J's own fallback through the classic API). Unlike the classic
     * `(String, Object)` overload, the promotion applies even to a lone throwable argument, mirroring
     * those routes. Without it, log4k would ignore the throwable as an excess formatting argument and
     * the stack trace would be lost.
     */
    override fun log(event: Slf4jLoggingEvent) {
        val keyValuePairs = event.keyValuePairs ?: emptyList()
        val kvpCallSite = callSiteFrom { key -> keyValuePairs.lastOrNull { it.key == key }?.value }
        val tags = keyValuePairs
            .filterNot { kvpCallSite != null && it.key in CODE_LOCATION_KEYS }
            .associate { it.key to (it.value ?: "null") }
        val arguments: Array<out Any?> = event.argumentArray ?: emptyArray()
        val trailingThrowable = if (event.throwable == null) arguments.lastOrNull() as? Throwable else null
        log4k.log(
            level = event.level.toLevel(),
            span = null,
            tags = tags,
            message = event.message ?: "null",
            arguments = if (trailingThrowable != null) arguments.copyOf(arguments.size - 1) else arguments,
            throwable = event.throwable ?: trailingThrowable,
            callSite = kvpCallSite ?: callSiteFromMdc(),
        )
    }

    private fun org.slf4j.event.Level.toLevel(): Level =
        when (this) {
            org.slf4j.event.Level.TRACE -> Level.TRACE
            org.slf4j.event.Level.DEBUG -> Level.DEBUG
            org.slf4j.event.Level.INFO -> Level.INFO
            org.slf4j.event.Level.WARN -> Level.WARN
            org.slf4j.event.Level.ERROR -> Level.ERROR
        }

    private fun callSiteFromMdc(): SourceLocation? = callSiteFrom { key -> MDC.get(key) }

    /**
     * Builds a [SourceLocation] from the code-location attributes reachable through [lookup] — the stable
     * OpenTelemetry keys first, their legacy spellings next, and logstash-logback-encoder's
     * `caller_*` caller-data fields last. Returns `null` when no file is recorded; a
     * missing/unparsable line becomes `0`, a missing function becomes `""` (with the legacy OTel
     * spelling, `code.namespace` + `code.function` are joined — and with the logstash fields,
     * `caller_class_name`'s simple name + `caller_method_name` — to mirror `ClassName.functionName`).
     */
    private fun callSiteFrom(lookup: (String) -> Any?): SourceLocation? {
        val file = (lookup(OpenTelemetryAttributes.CODE_FILE_PATH)
            ?: lookup(CODE_FILEPATH_LEGACY)
            ?: lookup(CALLER_FILE_NAME))?.toString()
            ?: return null
        val line = (lookup(OpenTelemetryAttributes.CODE_LINE_NUMBER)
            ?: lookup(CODE_LINENO_LEGACY)
            ?: lookup(CALLER_LINE_NUMBER))?.toString()?.toIntOrNull()
            ?: 0
        val function = lookup(OpenTelemetryAttributes.CODE_FUNCTION_NAME)?.toString()
            ?: legacyFunction(lookup)
            ?: callerFunction(lookup)
            ?: ""
        return SourceLocation(file = file, line = line, function = function)
    }

    private fun legacyFunction(lookup: (String) -> Any?): String? {
        val function = lookup(CODE_FUNCTION_LEGACY)?.toString() ?: return null
        val namespace = lookup(CODE_NAMESPACE_LEGACY)?.toString()
        return if (namespace.isNullOrBlank()) function else "$namespace.$function"
    }

    private fun callerFunction(lookup: (String) -> Any?): String? {
        val method = lookup(CALLER_METHOD_NAME)?.toString() ?: return null
        // logstash's caller_class_name is fully qualified; keep the simple name to mirror the
        // `ClassName.functionName` format the compiler plugin produces.
        val className = lookup(CALLER_CLASS_NAME)?.toString()?.substringAfterLast('.')
        return if (className.isNullOrBlank()) method else "$className.$method"
    }

    private companion object {
        // The pre-1.29 (pre-stability) spellings of the OpenTelemetry code attributes — still widely
        // emitted by older instrumentation, so they are accepted on the way in.
        private const val CODE_FILEPATH_LEGACY = "code.filepath"
        private const val CODE_LINENO_LEGACY = "code.lineno"
        private const val CODE_FUNCTION_LEGACY = "code.function"
        private const val CODE_NAMESPACE_LEGACY = "code.namespace"

        // logstash-logback-encoder's caller-data field names — the shape Logback-based JSON logs
        // (e.g., Spring Boot with the logstash encoder) use for their native caller data.
        private const val CALLER_FILE_NAME = "caller_file_name"
        private const val CALLER_LINE_NUMBER = "caller_line_number"
        private const val CALLER_METHOD_NAME = "caller_method_name"
        private const val CALLER_CLASS_NAME = "caller_class_name"

        /** Every key that may contribute to a [SourceLocation] — consumed from the key-value pairs when one is built. */
        private val CODE_LOCATION_KEYS = setOf(
            OpenTelemetryAttributes.CODE_FILE_PATH,
            OpenTelemetryAttributes.CODE_LINE_NUMBER,
            OpenTelemetryAttributes.CODE_FUNCTION_NAME,
            CODE_FILEPATH_LEGACY,
            CODE_LINENO_LEGACY,
            CODE_FUNCTION_LEGACY,
            CODE_NAMESPACE_LEGACY,
            CALLER_FILE_NAME,
            CALLER_LINE_NUMBER,
            CALLER_METHOD_NAME,
            CALLER_CLASS_NAME,
        )
    }
}
