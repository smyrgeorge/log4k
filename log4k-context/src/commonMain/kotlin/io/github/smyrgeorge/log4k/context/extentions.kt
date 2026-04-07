@file:Suppress("unused")

package io.github.smyrgeorge.log4k.context

import io.github.smyrgeorge.log4k.Level.DEBUG
import io.github.smyrgeorge.log4k.Level.ERROR
import io.github.smyrgeorge.log4k.Level.INFO
import io.github.smyrgeorge.log4k.Level.TRACE
import io.github.smyrgeorge.log4k.Level.WARN
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.TracingContext

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
//@formatter:on
