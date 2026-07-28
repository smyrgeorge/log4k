package io.github.smyrgeorge.log4k.impl.extensions

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggingEvent

/** Logs an event at the [Level.TRACE] level using the builder-style DSL (see [Logger.at]). */
inline fun Logger.atTrace(f: LoggingEvent.Builder.() -> Unit): Unit = at(Level.TRACE, f)

/** Logs an event at the [Level.DEBUG] level using the builder-style DSL (see [Logger.at]). */
inline fun Logger.atDebug(f: LoggingEvent.Builder.() -> Unit): Unit = at(Level.DEBUG, f)

/** Logs an event at the [Level.INFO] level using the builder-style DSL (see [Logger.at]). */
inline fun Logger.atInfo(f: LoggingEvent.Builder.() -> Unit): Unit = at(Level.INFO, f)

/** Logs an event at the [Level.WARN] level using the builder-style DSL (see [Logger.at]). */
inline fun Logger.atWarn(f: LoggingEvent.Builder.() -> Unit): Unit = at(Level.WARN, f)

/** Logs an event at the [Level.ERROR] level using the builder-style DSL (see [Logger.at]). */
inline fun Logger.atError(f: LoggingEvent.Builder.() -> Unit): Unit = at(Level.ERROR, f)
