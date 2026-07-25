@file:Suppress("unused")

package io.github.smyrgeorge.log4k.context

import io.github.smyrgeorge.log4k.Level.DEBUG
import io.github.smyrgeorge.log4k.Level.ERROR
import io.github.smyrgeorge.log4k.Level.INFO
import io.github.smyrgeorge.log4k.Level.TRACE
import io.github.smyrgeorge.log4k.Level.WARN
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingEvent.Span
import kotlin.jvm.JvmInline

//@formatter:off
context(c: TracingContext) inline fun Logger.trace(f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, c.currentOrNull(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.trace(t: Throwable, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, c.currentOrNull(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.debug(f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, c.currentOrNull(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.debug(t: Throwable, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, c.currentOrNull(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.info(f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, c.currentOrNull(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.info(t: Throwable, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, c.currentOrNull(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.warn(f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, c.currentOrNull(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.warn(t: Throwable, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, c.currentOrNull(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.error(f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, c.currentOrNull(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.error(t: Throwable, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, c.currentOrNull(), f(), emptyArray(), t) else Unit

context(c: TracingContext) fun Logger.trace(msg: String, vararg args: Any?): Unit =
    log(TRACE, c.currentOrNull(), msg, args, null)
context(c: TracingContext) fun Logger.trace(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(TRACE, c.currentOrNull(), msg, args, t)
context(c: TracingContext) fun Logger.debug(msg: String, vararg args: Any?): Unit =
    log(DEBUG, c.currentOrNull(), msg, args, null)
context(c: TracingContext) fun Logger.debug(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(DEBUG, c.currentOrNull(), msg, args, t)
context(c: TracingContext) fun Logger.info(msg: String, vararg args: Any?): Unit =
    log(INFO, c.currentOrNull(), msg, args, null)
context(c: TracingContext) fun Logger.info(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(INFO, c.currentOrNull(), msg, args, t)
context(c: TracingContext) fun Logger.warn(msg: String, vararg args: Any?): Unit =
    log(WARN, c.currentOrNull(), msg, args, null)
context(c: TracingContext) fun Logger.warn(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(WARN, c.currentOrNull(), msg, args, t)
context(c: TracingContext) fun Logger.error(msg: String, vararg args: Any?): Unit =
    log(ERROR, c.currentOrNull(), msg, args, null)
context(c: TracingContext) fun Logger.error(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(ERROR, c.currentOrNull(), msg, args, t)

context(s: Span) inline fun Logger.trace(f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, s, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.trace(t: Throwable, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, s, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.debug(f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, s, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.debug(t: Throwable, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, s, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.info(f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, s, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.info(t: Throwable, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, s, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.warn(f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, s, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.warn(t: Throwable, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, s, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.error(f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, s, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.error(t: Throwable, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, s, f(), emptyArray(), t) else Unit

context(s: Span) fun Logger.trace(msg: String, vararg args: Any?): Unit = log(TRACE, s, msg, args, null)
context(s: Span) fun Logger.trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, s, msg, args, t)
context(s: Span) fun Logger.debug(msg: String, vararg args: Any?): Unit = log(DEBUG, s, msg, args, null)
context(s: Span) fun Logger.debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, s, msg, args, t)
context(s: Span) fun Logger.info(msg: String, vararg args: Any?): Unit = log(INFO, s, msg, args, null)
context(s: Span) fun Logger.info(msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, s, msg, args, t)
context(s: Span) fun Logger.warn(msg: String, vararg args: Any?): Unit = log(WARN, s, msg, args, null)
context(s: Span) fun Logger.warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, s, msg, args, t)
context(s: Span) fun Logger.error(msg: String, vararg args: Any?): Unit = log(ERROR, s, msg, args, null)
context(s: Span) fun Logger.error(msg: String, t: Throwable, vararg args: Any?): Unit = log(ERROR, s, msg, args, t)

/**
 * Escape hatch to the **classic** (non-context) logging API from inside a `TracingContext` / `Span`
 * scope, where the context-aware overloads above would otherwise win (or be ambiguous).
 *
 * `log.classic.info { … }` logs with **no** span auto-attached — exactly like the `log4k-classic`
 * extensions — while `log.classic.info(span) { … }` attaches the given [Span] explicitly. It is also
 * the only way to reach the classic API from a module that depends solely on `log4k-context` (which
 * does not re-export `log4k-classic`).
 *
 * [Log4kClassic] is a zero-cost [JvmInline] wrapper carrying the [Logger]; the forwarders below mirror
 * the `log4k-classic` overloads by delegating straight to [Logger.log].
 */
val Logger.classic: Log4kClassic get() = Log4kClassic(this)

@JvmInline
value class Log4kClassic @PublishedApi internal constructor(@PublishedApi internal val logger: Logger)

inline fun Log4kClassic.trace(f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, null, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.trace(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, null, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.debug(f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, null, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.debug(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, null, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.info(f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, null, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.info(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, null, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.warn(f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, null, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.warn(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, null, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.error(f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, null, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.error(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, null, f(), emptyArray(), t) else Unit

fun Log4kClassic.trace(msg: String, vararg args: Any?): Unit = logger.log(TRACE, null, msg, args, null)
fun Log4kClassic.trace(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(TRACE, null, msg, args, t)
fun Log4kClassic.debug(msg: String, vararg args: Any?): Unit = logger.log(DEBUG, null, msg, args, null)
fun Log4kClassic.debug(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(DEBUG, null, msg, args, t)
fun Log4kClassic.info(msg: String, vararg args: Any?): Unit = logger.log(INFO, null, msg, args, null)
fun Log4kClassic.info(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(INFO, null, msg, args, t)
fun Log4kClassic.warn(msg: String, vararg args: Any?): Unit = logger.log(WARN, null, msg, args, null)
fun Log4kClassic.warn(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(WARN, null, msg, args, t)
fun Log4kClassic.error(msg: String, vararg args: Any?): Unit = logger.log(ERROR, null, msg, args, null)
fun Log4kClassic.error(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(ERROR, null, msg, args, t)

inline fun Log4kClassic.trace(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, span, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.trace(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, span, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.debug(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, span, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.debug(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, span, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.info(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, span, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.info(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, span, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.warn(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, span, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.warn(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, span, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.error(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, span, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.error(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, span, f(), emptyArray(), t) else Unit

fun Log4kClassic.trace(span: Span, msg: String, vararg args: Any?): Unit = logger.log(TRACE, span, msg, args, null)
fun Log4kClassic.trace(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(TRACE, span, msg, args, t)
fun Log4kClassic.debug(span: Span, msg: String, vararg args: Any?): Unit = logger.log(DEBUG, span, msg, args, null)
fun Log4kClassic.debug(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(DEBUG, span, msg, args, t)
fun Log4kClassic.info(span: Span, msg: String, vararg args: Any?): Unit = logger.log(INFO, span, msg, args, null)
fun Log4kClassic.info(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(INFO, span, msg, args, t)
fun Log4kClassic.warn(span: Span, msg: String, vararg args: Any?): Unit = logger.log(WARN, span, msg, args, null)
fun Log4kClassic.warn(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(WARN, span, msg, args, t)
fun Log4kClassic.error(span: Span, msg: String, vararg args: Any?): Unit = logger.log(ERROR, span, msg, args, null)
fun Log4kClassic.error(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(ERROR, span, msg, args, t)
//@formatter:on
