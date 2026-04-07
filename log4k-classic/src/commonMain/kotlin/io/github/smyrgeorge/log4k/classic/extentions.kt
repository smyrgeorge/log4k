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
inline fun Logger.trace(f: () -> String): Unit = if (TRACE.enabled()) trace(f()) else Unit
inline fun Logger.trace(t: Throwable, f: () -> String): Unit = if (TRACE.enabled()) trace(f(), t) else Unit
inline fun Logger.debug(f: () -> String): Unit = if (DEBUG.enabled()) debug(f()) else Unit
inline fun Logger.debug(t: Throwable, f: () -> String): Unit = if (DEBUG.enabled()) debug(f(), t) else Unit
inline fun Logger.info(f: () -> String): Unit = if (INFO.enabled()) info(f()) else Unit
inline fun Logger.info(t: Throwable, f: () -> String): Unit = if (INFO.enabled()) info(f(), t) else Unit
inline fun Logger.warn(f: () -> String): Unit = if (WARN.enabled()) warn(f()) else Unit
inline fun Logger.warn(t: Throwable, f: () -> String): Unit = if (WARN.enabled()) warn(f(), t) else Unit
inline fun Logger.error(f: () -> String): Unit = if (ERROR.enabled()) error(f()) else Unit
inline fun Logger.error(t: Throwable, f: () -> String): Unit = if (ERROR.enabled()) error(f(), t) else Unit

inline fun Logger.trace(span: Span, f: () -> String): Unit = if (TRACE.enabled()) trace(span, f()) else Unit
inline fun Logger.trace(span: Span, t: Throwable, f: () -> String): Unit = if (TRACE.enabled()) trace(span, f(), t) else Unit
inline fun Logger.debug(span: Span, f: () -> String): Unit = if (DEBUG.enabled()) debug(span, f()) else Unit
inline fun Logger.debug(span: Span, t: Throwable, f: () -> String): Unit = if (DEBUG.enabled()) debug(span, f(), t) else Unit
inline fun Logger.info(span: Span, f: () -> String): Unit = if (INFO.enabled()) info(span, f()) else Unit
inline fun Logger.info(span: Span, t: Throwable, f: () -> String): Unit = if (INFO.enabled()) info(span, f(), t) else Unit
inline fun Logger.warn(span: Span, f: () -> String): Unit = if (WARN.enabled()) warn(span, f()) else Unit
inline fun Logger.warn(span: Span, t: Throwable, f: () -> String): Unit = if (WARN.enabled()) warn(span, f(), t) else Unit
inline fun Logger.error(span: Span, f: () -> String): Unit = if (ERROR.enabled()) error(span, f()) else Unit
inline fun Logger.error(span: Span, t: Throwable, f: () -> String): Unit = if (ERROR.enabled()) error(span, f(), t) else Unit

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
