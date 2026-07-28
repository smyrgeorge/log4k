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
import io.github.smyrgeorge.log4k.impl.Tags
import kotlin.jvm.JvmInline

//@formatter:off
context(c: TracingContext) inline fun Logger.trace(f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, c.currentOrNull(), emptyMap(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.trace(t: Throwable, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, c.currentOrNull(), emptyMap(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.debug(f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, c.currentOrNull(), emptyMap(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.debug(t: Throwable, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, c.currentOrNull(), emptyMap(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.info(f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, c.currentOrNull(), emptyMap(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.info(t: Throwable, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, c.currentOrNull(), emptyMap(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.warn(f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, c.currentOrNull(), emptyMap(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.warn(t: Throwable, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, c.currentOrNull(), emptyMap(), f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.error(f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, c.currentOrNull(), emptyMap(), f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.error(t: Throwable, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, c.currentOrNull(), emptyMap(), f(), emptyArray(), t) else Unit

context(c: TracingContext) inline fun Logger.trace(tags: Tags, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, c.currentOrNull(), tags, f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.trace(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, c.currentOrNull(), tags, f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.debug(tags: Tags, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, c.currentOrNull(), tags, f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.debug(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, c.currentOrNull(), tags, f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.info(tags: Tags, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, c.currentOrNull(), tags, f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.info(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, c.currentOrNull(), tags, f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.warn(tags: Tags, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, c.currentOrNull(), tags, f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.warn(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, c.currentOrNull(), tags, f(), emptyArray(), t) else Unit
context(c: TracingContext) inline fun Logger.error(tags: Tags, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, c.currentOrNull(), tags, f(), emptyArray(), null) else Unit
context(c: TracingContext) inline fun Logger.error(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, c.currentOrNull(), tags, f(), emptyArray(), t) else Unit

context(c: TracingContext) fun Logger.trace(msg: String, vararg args: Any?): Unit =
    log(TRACE, c.currentOrNull(), emptyMap(), msg, args, null)
context(c: TracingContext) fun Logger.trace(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(TRACE, c.currentOrNull(), emptyMap(), msg, args, t)
context(c: TracingContext) fun Logger.debug(msg: String, vararg args: Any?): Unit =
    log(DEBUG, c.currentOrNull(), emptyMap(), msg, args, null)
context(c: TracingContext) fun Logger.debug(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(DEBUG, c.currentOrNull(), emptyMap(), msg, args, t)
context(c: TracingContext) fun Logger.info(msg: String, vararg args: Any?): Unit =
    log(INFO, c.currentOrNull(), emptyMap(), msg, args, null)
context(c: TracingContext) fun Logger.info(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(INFO, c.currentOrNull(), emptyMap(), msg, args, t)
context(c: TracingContext) fun Logger.warn(msg: String, vararg args: Any?): Unit =
    log(WARN, c.currentOrNull(), emptyMap(), msg, args, null)
context(c: TracingContext) fun Logger.warn(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(WARN, c.currentOrNull(), emptyMap(), msg, args, t)
context(c: TracingContext) fun Logger.error(msg: String, vararg args: Any?): Unit =
    log(ERROR, c.currentOrNull(), emptyMap(), msg, args, null)
context(c: TracingContext) fun Logger.error(msg: String, t: Throwable, vararg args: Any?): Unit =
    log(ERROR, c.currentOrNull(), emptyMap(), msg, args, t)

context(c: TracingContext) fun Logger.trace(tags: Tags, msg: String, vararg args: Any?): Unit =
    log(TRACE, c.currentOrNull(), tags, msg, args, null)
context(c: TracingContext) fun Logger.trace(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit =
    log(TRACE, c.currentOrNull(), tags, msg, args, t)
context(c: TracingContext) fun Logger.debug(tags: Tags, msg: String, vararg args: Any?): Unit =
    log(DEBUG, c.currentOrNull(), tags, msg, args, null)
context(c: TracingContext) fun Logger.debug(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit =
    log(DEBUG, c.currentOrNull(), tags, msg, args, t)
context(c: TracingContext) fun Logger.info(tags: Tags, msg: String, vararg args: Any?): Unit =
    log(INFO, c.currentOrNull(), tags, msg, args, null)
context(c: TracingContext) fun Logger.info(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit =
    log(INFO, c.currentOrNull(), tags, msg, args, t)
context(c: TracingContext) fun Logger.warn(tags: Tags, msg: String, vararg args: Any?): Unit =
    log(WARN, c.currentOrNull(), tags, msg, args, null)
context(c: TracingContext) fun Logger.warn(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit =
    log(WARN, c.currentOrNull(), tags, msg, args, t)
context(c: TracingContext) fun Logger.error(tags: Tags, msg: String, vararg args: Any?): Unit =
    log(ERROR, c.currentOrNull(), tags, msg, args, null)
context(c: TracingContext) fun Logger.error(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit =
    log(ERROR, c.currentOrNull(), tags, msg, args, t)

context(s: Span) inline fun Logger.trace(f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, s, emptyMap(), f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.trace(t: Throwable, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, s, emptyMap(), f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.debug(f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, s, emptyMap(), f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.debug(t: Throwable, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, s, emptyMap(), f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.info(f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, s, emptyMap(), f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.info(t: Throwable, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, s, emptyMap(), f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.warn(f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, s, emptyMap(), f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.warn(t: Throwable, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, s, emptyMap(), f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.error(f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, s, emptyMap(), f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.error(t: Throwable, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, s, emptyMap(), f(), emptyArray(), t) else Unit

context(s: Span) inline fun Logger.trace(tags: Tags, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, s, tags, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.trace(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (TRACE.enabled()) log(TRACE, s, tags, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.debug(tags: Tags, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, s, tags, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.debug(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (DEBUG.enabled()) log(DEBUG, s, tags, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.info(tags: Tags, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, s, tags, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.info(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (INFO.enabled()) log(INFO, s, tags, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.warn(tags: Tags, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, s, tags, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.warn(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (WARN.enabled()) log(WARN, s, tags, f(), emptyArray(), t) else Unit
context(s: Span) inline fun Logger.error(tags: Tags, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, s, tags, f(), emptyArray(), null) else Unit
context(s: Span) inline fun Logger.error(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (ERROR.enabled()) log(ERROR, s, tags, f(), emptyArray(), t) else Unit

context(s: Span) fun Logger.trace(msg: String, vararg args: Any?): Unit = log(TRACE, s, emptyMap(), msg, args, null)
context(s: Span) fun Logger.trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, s, emptyMap(), msg, args, t)
context(s: Span) fun Logger.debug(msg: String, vararg args: Any?): Unit = log(DEBUG, s, emptyMap(), msg, args, null)
context(s: Span) fun Logger.debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, s, emptyMap(), msg, args, t)
context(s: Span) fun Logger.info(msg: String, vararg args: Any?): Unit = log(INFO, s, emptyMap(), msg, args, null)
context(s: Span) fun Logger.info(msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, s, emptyMap(), msg, args, t)
context(s: Span) fun Logger.warn(msg: String, vararg args: Any?): Unit = log(WARN, s, emptyMap(), msg, args, null)
context(s: Span) fun Logger.warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, s, emptyMap(), msg, args, t)
context(s: Span) fun Logger.error(msg: String, vararg args: Any?): Unit = log(ERROR, s, emptyMap(), msg, args, null)
context(s: Span) fun Logger.error(msg: String, t: Throwable, vararg args: Any?): Unit = log(ERROR, s, emptyMap(), msg, args, t)

context(s: Span) fun Logger.trace(tags: Tags, msg: String, vararg args: Any?): Unit = log(TRACE, s, tags, msg, args, null)
context(s: Span) fun Logger.trace(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, s, tags, msg, args, t)
context(s: Span) fun Logger.debug(tags: Tags, msg: String, vararg args: Any?): Unit = log(DEBUG, s, tags, msg, args, null)
context(s: Span) fun Logger.debug(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, s, tags, msg, args, t)
context(s: Span) fun Logger.info(tags: Tags, msg: String, vararg args: Any?): Unit = log(INFO, s, tags, msg, args, null)
context(s: Span) fun Logger.info(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, s, tags, msg, args, t)
context(s: Span) fun Logger.warn(tags: Tags, msg: String, vararg args: Any?): Unit = log(WARN, s, tags, msg, args, null)
context(s: Span) fun Logger.warn(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, s, tags, msg, args, t)
context(s: Span) fun Logger.error(tags: Tags, msg: String, vararg args: Any?): Unit = log(ERROR, s, tags, msg, args, null)
context(s: Span) fun Logger.error(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = log(ERROR, s, tags, msg, args, t)

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
    if (logger.isEnabled(TRACE)) logger.log(TRACE, null, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.trace(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, null, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.debug(f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, null, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.debug(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, null, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.info(f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, null, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.info(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, null, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.warn(f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, null, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.warn(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, null, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.error(f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, null, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.error(t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, null, emptyMap(), f(), emptyArray(), t) else Unit

inline fun Log4kClassic.trace(tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, null, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.trace(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, null, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.debug(tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, null, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.debug(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, null, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.info(tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, null, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.info(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, null, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.warn(tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, null, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.warn(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, null, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.error(tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, null, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.error(tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, null, tags, f(), emptyArray(), t) else Unit

inline fun Log4kClassic.trace(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, span, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.trace(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, span, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.debug(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, span, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.debug(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, span, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.info(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, span, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.info(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, span, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.warn(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, span, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.warn(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, span, emptyMap(), f(), emptyArray(), t) else Unit
inline fun Log4kClassic.error(span: Span, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, span, emptyMap(), f(), emptyArray(), null) else Unit
inline fun Log4kClassic.error(span: Span, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, span, emptyMap(), f(), emptyArray(), t) else Unit

inline fun Log4kClassic.trace(span: Span, tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, span, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.trace(span: Span, tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(TRACE)) logger.log(TRACE, span, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.debug(span: Span, tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, span, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.debug(span: Span, tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(DEBUG)) logger.log(DEBUG, span, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.info(span: Span, tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, span, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.info(span: Span, tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(INFO)) logger.log(INFO, span, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.warn(span: Span, tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, span, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.warn(span: Span, tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(WARN)) logger.log(WARN, span, tags, f(), emptyArray(), t) else Unit
inline fun Log4kClassic.error(span: Span, tags: Tags, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, span, tags, f(), emptyArray(), null) else Unit
inline fun Log4kClassic.error(span: Span, tags: Tags, t: Throwable, f: () -> String): Unit =
    if (logger.isEnabled(ERROR)) logger.log(ERROR, span, tags, f(), emptyArray(), t) else Unit

fun Log4kClassic.trace(msg: String, vararg args: Any?): Unit = logger.log(TRACE, null, emptyMap(), msg, args, null)
fun Log4kClassic.trace(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(TRACE, null, emptyMap(), msg, args, t)
fun Log4kClassic.debug(msg: String, vararg args: Any?): Unit = logger.log(DEBUG, null, emptyMap(), msg, args, null)
fun Log4kClassic.debug(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(DEBUG, null, emptyMap(), msg, args, t)
fun Log4kClassic.info(msg: String, vararg args: Any?): Unit = logger.log(INFO, null, emptyMap(), msg, args, null)
fun Log4kClassic.info(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(INFO, null, emptyMap(), msg, args, t)
fun Log4kClassic.warn(msg: String, vararg args: Any?): Unit = logger.log(WARN, null, emptyMap(), msg, args, null)
fun Log4kClassic.warn(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(WARN, null, emptyMap(), msg, args, t)
fun Log4kClassic.error(msg: String, vararg args: Any?): Unit = logger.log(ERROR, null, emptyMap(), msg, args, null)
fun Log4kClassic.error(msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(ERROR, null, emptyMap(), msg, args, t)

fun Log4kClassic.trace(tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(TRACE, null, tags, msg, args, null)
fun Log4kClassic.trace(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(TRACE, null, tags, msg, args, t)
fun Log4kClassic.debug(tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(DEBUG, null, tags, msg, args, null)
fun Log4kClassic.debug(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(DEBUG, null, tags, msg, args, t)
fun Log4kClassic.info(tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(INFO, null, tags, msg, args, null)
fun Log4kClassic.info(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(INFO, null, tags, msg, args, t)
fun Log4kClassic.warn(tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(WARN, null, tags, msg, args, null)
fun Log4kClassic.warn(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(WARN, null, tags, msg, args, t)
fun Log4kClassic.error(tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(ERROR, null, tags, msg, args, null)
fun Log4kClassic.error(tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(ERROR, null, tags, msg, args, t)

fun Log4kClassic.trace(span: Span, msg: String, vararg args: Any?): Unit = logger.log(TRACE, span, emptyMap(), msg, args, null)
fun Log4kClassic.trace(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(TRACE, span, emptyMap(), msg, args, t)
fun Log4kClassic.debug(span: Span, msg: String, vararg args: Any?): Unit = logger.log(DEBUG, span, emptyMap(), msg, args, null)
fun Log4kClassic.debug(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(DEBUG, span, emptyMap(), msg, args, t)
fun Log4kClassic.info(span: Span, msg: String, vararg args: Any?): Unit = logger.log(INFO, span, emptyMap(), msg, args, null)
fun Log4kClassic.info(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(INFO, span, emptyMap(), msg, args, t)
fun Log4kClassic.warn(span: Span, msg: String, vararg args: Any?): Unit = logger.log(WARN, span, emptyMap(), msg, args, null)
fun Log4kClassic.warn(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(WARN, span, emptyMap(), msg, args, t)
fun Log4kClassic.error(span: Span, msg: String, vararg args: Any?): Unit = logger.log(ERROR, span, emptyMap(), msg, args, null)
fun Log4kClassic.error(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(ERROR, span, emptyMap(), msg, args, t)

fun Log4kClassic.trace(span: Span, tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(TRACE, span, tags, msg, args, null)
fun Log4kClassic.trace(span: Span, tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(TRACE, span, tags, msg, args, t)
fun Log4kClassic.debug(span: Span, tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(DEBUG, span, tags, msg, args, null)
fun Log4kClassic.debug(span: Span, tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(DEBUG, span, tags, msg, args, t)
fun Log4kClassic.info(span: Span, tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(INFO, span, tags, msg, args, null)
fun Log4kClassic.info(span: Span, tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(INFO, span, tags, msg, args, t)
fun Log4kClassic.warn(span: Span, tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(WARN, span, tags, msg, args, null)
fun Log4kClassic.warn(span: Span, tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(WARN, span, tags, msg, args, t)
fun Log4kClassic.error(span: Span, tags: Tags, msg: String, vararg args: Any?): Unit = logger.log(ERROR, span, tags, msg, args, null)
fun Log4kClassic.error(span: Span, tags: Tags, msg: String, t: Throwable, vararg args: Any?): Unit = logger.log(ERROR, span, tags, msg, args, t)
//@formatter:on
