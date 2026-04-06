@file:Suppress("unused")

package io.github.smyrgeorge.log4k.classic

import io.github.smyrgeorge.log4k.Level.DEBUG
import io.github.smyrgeorge.log4k.Level.ERROR
import io.github.smyrgeorge.log4k.Level.INFO
import io.github.smyrgeorge.log4k.Level.TRACE
import io.github.smyrgeorge.log4k.Level.WARN
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.TracingEvent.Span

//@formatter:off
inline fun Logger.trace(f: () -> String): Unit = trace(f())
inline fun Logger.trace(t: Throwable, f: () -> String): Unit = trace(f(), t)
inline fun Logger.debug(f: () -> String): Unit = debug(f())
inline fun Logger.debug(t: Throwable, f: () -> String): Unit = debug(f(), t)
inline fun Logger.info(f: () -> String): Unit = info(f())
inline fun Logger.info(t: Throwable, f: () -> String): Unit = info(f(), t)
inline fun Logger.warn(f: () -> String): Unit = warn(f())
inline fun Logger.warn(t: Throwable, f: () -> String): Unit = warn(f(), t)
inline fun Logger.error(f: () -> String): Unit = error(f())
inline fun Logger.error(t: Throwable, f: () -> String): Unit = error(f(), t)

inline fun Logger.trace(span: Span, f: () -> String): Unit = trace(span, f())
inline fun Logger.trace(span: Span, t: Throwable, f: () -> String): Unit = trace(span, f(), t)
inline fun Logger.debug(span: Span, f: () -> String): Unit = debug(span, f())
inline fun Logger.debug(span: Span, t: Throwable, f: () -> String): Unit = debug(span, f(), t)
inline fun Logger.info(span: Span, f: () -> String): Unit = info(span, f())
inline fun Logger.info(span: Span, t: Throwable, f: () -> String): Unit = info(span, f(), t)
inline fun Logger.warn(span: Span, f: () -> String): Unit = warn(span, f())
inline fun Logger.warn(span: Span, t: Throwable, f: () -> String): Unit = warn(span, f(), t)
inline fun Logger.error(span: Span, f: () -> String): Unit = error(span, f())
inline fun Logger.error(span: Span, t: Throwable, f: () -> String): Unit = error(span, f(), t)

fun Logger.trace(msg: String, vararg args: Any?): Unit = log(TRACE, null, msg, args, null)
fun Logger.trace(msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, null, msg, args, t)
fun Logger.debug(msg: String, vararg args: Any?): Unit = log(DEBUG, null, msg, args, null)
fun Logger.debug(msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, null, msg, args, t)
fun Logger.info(msg: String, vararg args: Any?): Unit = log(INFO, null, msg, args, null)
fun Logger.info(msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, null, msg, args, t)
fun Logger.warn(msg: String, vararg args: Any?): Unit = log(WARN, null, msg, args, null)
fun Logger.warn(msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, null, msg, args, t)
fun Logger.error(msg: String, vararg args: Any?): Unit = log(ERROR, null, msg, args, null)
fun Logger.error(msg: String, t: Throwable, vararg args: Any?): Unit = log(ERROR, null, msg, args, t)

fun Logger.trace(span: Span, msg: String, vararg args: Any?): Unit = log(TRACE, span, msg, args, null)
fun Logger.trace(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(TRACE, span, msg, args, t)
fun Logger.debug(span: Span, msg: String, vararg args: Any?): Unit = log(DEBUG, span, msg, args, null)
fun Logger.debug(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(DEBUG, span, msg, args, t)
fun Logger.info(span: Span, msg: String, vararg args: Any?): Unit = log(INFO, span, msg, args, null)
fun Logger.info(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(INFO, span, msg, args, t)
fun Logger.warn(span: Span, msg: String, vararg args: Any?): Unit = log(WARN, span, msg, args, null)
fun Logger.warn(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(WARN, span, msg, args, t)
fun Logger.error(span: Span, msg: String, vararg args: Any?): Unit = log(ERROR, span, msg, args, null)
fun Logger.error(span: Span, msg: String, t: Throwable, vararg args: Any?): Unit = log(ERROR, span, msg, args, t)
//@formatter:on
