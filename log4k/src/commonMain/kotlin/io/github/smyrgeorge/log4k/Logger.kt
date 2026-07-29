package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.TracingEvent.Span
import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.Tags
import io.github.smyrgeorge.log4k.impl.registry.CollectorRegistry
import kotlinx.coroutines.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * An abstract logger class providing logging functionality across different levels.
 * Extends the `CollectorRegistry.Collector` class.
 *
 * @property name The name identifier for the logger.
 * @property level The logging level threshold.
 */
abstract class Logger(
    final override val name: String,
    final override var level: Level
) : CollectorRegistry.Collector {
    override var levelBeforeMute: Level = level

    /**
     * Logs a message with the specified logging level and additional context.
     *
     * @param level The logging level of the event.
     * @param span An optional span that can be used for tracing the context.
     * @param tags Structured key-value dimensions attached to the event, kept separate from the
     *             message text (mirroring the tags carried by tracing spans and metering events).
     * @param message The log message to be recorded.
     * @param arguments Additional arguments to be included in the log event.
     * @param throwable An optional throwable associated with the log event.
     */
    fun log(
        level: Level,
        span: Span?,
        tags: Tags,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?,
    ): Unit = log(level, span, tags, message, arguments, throwable, null)

    /**
     * Logs a message with the specified logging level, additional context, and the source location
     * of the call.
     *
     * This overload is the target the `log4k-compiler-plugin` rewrites logging calls to: the plugin
     * appends a compile-time [SourceLocation] so the emitted event carries accurate file/line/function
     * information on every platform, without any runtime stack-walking. It can also be called
     * directly when the call site is known by other means.
     *
     * @param level The logging level of the event.
     * @param span An optional span that can be used for tracing the context.
     * @param tags Structured key-value dimensions attached to the event, kept separate from the
     *             message text (mirroring the tags carried by tracing spans and metering events).
     * @param message The log message to be recorded.
     * @param arguments Additional arguments to be included in the log event.
     * @param throwable An optional throwable associated with the log event.
     * @param callSite The source location of the call, or `null` when unknown.
     */
    fun log(
        level: Level,
        span: Span?,
        tags: Tags,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?,
        callSite: SourceLocation?,
    ) {
        if (!level.enabled()) return
        val event = toLoggingEvent(level, span, tags, message, arguments, throwable, callSite)
        RootLogger.log(event)
    }

    /**
     * Converts the provided logging information into a `LoggingEvent`.
     *
     * @param level The logging level of the event.
     * @param span An optional span that can be used for tracing the context.
     * @param tags Structured key-value dimensions attached to the event.
     * @param message The log message to be recorded.
     * @param arguments Additional arguments to be included in the log event.
     * @param throwable An optional throwable associated with the log event.
     * @param callSite The source location of the call, or `null` when unknown.
     * @return A `LoggingEvent` representing the logging details.
     */
    abstract fun toLoggingEvent(
        level: Level,
        span: Span?,
        tags: Tags,
        message: String,
        arguments: Array<out Any?>,
        throwable: Throwable?,
        callSite: SourceLocation?,
    ): LoggingEvent

    /**
     * Checks if logging is enabled for the specified logging level.
     *
     * @param level The logging level to be checked.
     * @return `true` if logging is enabled for the specified level, `false` otherwise.
     */
    fun isEnabled(level: Level): Boolean = level.enabled()
    fun Level.enabled(): Boolean = this != Level.OFF && ordinal >= level.ordinal

    /**
     * Logs an event at the given [level] using the builder-style DSL.
     *
     * The [f] block configures a fresh [LoggingEvent.Builder]; its properties map one-to-one onto
     * the parameters of [log], which is invoked once the block returns:
     *
     * ```kotlin
     * log.at(Level.WARN) {
     *     message = "foo $bar"
     *     cause = exception
     *     tags = buildMap(capacity = 3) {
     *         put("foo", 1)
     *         put("bar", "x")
     *         put("obj", Pair(2, 3))
     *     }
     * }
     * ```
     *
     * The block is executed only when [level] is enabled for this logger, so building the message,
     * tags, or any other property costs nothing when the event is filtered out. Level-named
     * shorthands (`atTrace`, `atDebug`, `atInfo`, `atWarn`, `atError`) are available as extension
     * functions in the `io.github.smyrgeorge.log4k.impl.extensions` package.
     *
     * @param level The logging level of the event.
     * @param f The block that configures the event's [LoggingEvent.Builder].
     */
    inline fun at(level: Level, f: LoggingEvent.Builder.() -> Unit): Unit = at(level, null, f)

    /**
     * Logs an event at the given [level] using the builder-style DSL, attaching the source location
     * of the call (see [at] for the DSL itself).
     *
     * This overload is the target the `log4k-compiler-plugin` rewrites `at`/`atTrace`/…/`atError`
     * calls to: the plugin injects a compile-time [SourceLocation] so the emitted event carries accurate
     * file/line/function information. It can also be called directly when the call site is known by
     * other means.
     *
     * @param level The logging level of the event.
     * @param callSite The source location of the call, or `null` when unknown.
     * @param f The block that configures the event's [LoggingEvent.Builder].
     */
    inline fun at(level: Level, callSite: SourceLocation?, f: LoggingEvent.Builder.() -> Unit) {
        if (!level.enabled()) return
        val builder = LoggingEvent.Builder().apply(f)
        log(level, builder.span, builder.tags, builder.message, builder.arguments, builder.cause, callSite)
    }

    /**
     * Executes [f] while emitting entry/exit log lines around it, and an error line if it throws.
     *
     * This is the runtime helper the `log4k-compiler-plugin` generates a call to when a function is
     * annotated with [io.github.smyrgeorge.log4k.annotation.Logged]. It is `inline`, so it works for
     * both regular and `suspend` functions, and it emits every line through [log] directly.
     *
     * Emitted lines:
     * - `"→ name(args)"` on entry, at [level].
     * - `"← name = result (duration)"` on normal completion, at [level] — or `"← name (duration)"`
     *   when [f] exits through a non-local return or is cancelled (there is no result value to
     *   render on those paths).
     * - `"✗ name failed (duration)"` at [Level.ERROR] — with the throwable attached — if [f] throws;
     *   the throwable is then rethrown.
     *
     * A [CancellationException] is rethrown *without* the `"✗ failed"` ERROR line: coroutine
     * cancellation is part of normal control flow, not an application error (mirrors [Tracer.span]
     * and [Meter.Timed.measure]). The neutral `"← name (duration)"` completion line is still
     * emitted, so the entry line is never left dangling.
     *
     * The entry/exit lines are built only when [level] is enabled, so a disabled logger costs no
     * string building — in particular, the result is not `toString()`ed. When it is rendered, it is
     * rendered defensively: a throwing `toString()` must never fail a call that already succeeded.
     *
     * @param T The type of the result produced by [f].
     * @param level The level used for the entry/exit lines.
     * @param span The span (if any) to attach to every emitted line, correlating the logs with a trace.
     * @param tags Structured key-value dimensions attached to every emitted line.
     * @param name The (already formatted) name of the instrumented function.
     * @param args The (already formatted) argument list rendered inside the entry line's parentheses.
     * @param f The block to execute.
     * @return The result produced by [f].
     */
    inline fun <T> logged(
        level: Level,
        span: Span?,
        tags: Tags,
        name: String,
        args: String,
        f: () -> T
    ): T = logged(level = level, span = span, tags = tags, name = name, args = args, callSite = null, f = f)

    /**
     * [logged] with the source location of the instrumented function attached to every emitted line.
     *
     * This overload is the one the `log4k-compiler-plugin` targets: the injected [callSite] describes
     * the **declaration** of the `@Logged` function (its file, line, and `ClassName.functionName`),
     * so the entry/exit/failure lines are attributed to the annotated function instead of carrying no
     * location at all.
     *
     * @param callSite The declaration site of the instrumented function, or `null` when unknown.
     */
    inline fun <T> logged(
        level: Level,
        span: Span?,
        tags: Tags,
        name: String,
        args: String,
        callSite: SourceLocation?,
        f: () -> T
    ): T {
        if (isEnabled(level)) log(level, span, tags, "→ $name($args)", emptyArray<Any?>(), null, callSite)
        val mark = TimeSource.Monotonic.markNow()
        var completed = false
        try {
            return f().also { result ->
                completed = true
                if (isEnabled(level)) {
                    val rendered = runCatching { result.toString() }.getOrElse { "<toString() failed>" }
                    log(
                        level = level,
                        span = span,
                        tags = tags,
                        message = "← $name = $rendered (${mark.elapsedNow()})",
                        arguments = emptyArray<Any?>(),
                        throwable = null,
                        callSite = callSite
                    )
                }
            }
        } catch (e: CancellationException) {
            // Cancellation is a normal control flow, not a failure: no "✗ failed" ERROR line.
            // `completed` stays false, so the finally below emits the neutral completion line.
            throw e
        } catch (e: Throwable) {
            completed = true
            log(Level.ERROR, span, tags, "✗ $name failed (${mark.elapsedNow()})", emptyArray<Any?>(), e, callSite)
            throw e
        } finally {
            // Must stay a single unconditional call: the compiler does not re-emit a conditional
            // `finally` block on the non-local-return path of an inlined lambda (the condition
            // lives inside the helper instead). Mirrors the `span {}`/`measure` helpers' shape.
            loggedCompletion(completed, level, span, tags, name, mark.elapsedNow(), callSite)
        }
    }

    /**
     * Emits the `"← name (duration)"` completion line for a [logged] block that exited through a
     * non-local return or a [CancellationException] (no result value exists on those paths). A
     * no-op when the block [completed] through the normal or exception path, or when [level] is
     * disabled.
     */
    @PublishedApi
    internal fun loggedCompletion(
        completed: Boolean,
        level: Level,
        span: Span?,
        tags: Tags,
        name: String,
        elapsed: Duration,
        callSite: SourceLocation?
    ) {
        if (completed || !isEnabled(level)) return
        log(level, span, tags, "← $name ($elapsed)", emptyArray<Any?>(), null, callSite)
    }

    companion object {
        val registry = CollectorRegistry<Logger>()
        var factory: LoggerFactory = SimpleLoggerFactory()
        fun of(name: String): Logger = factory.get(name)
        fun of(clazz: KClass<*>): Logger = factory.get(clazz)
    }
}
